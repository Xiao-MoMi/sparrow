package net.momirealms.sparrow.teleport.search;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.util.Dimensions;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * 根据玩家尺寸检查候选位置的地形安全, 包含世界边界, 身体碰撞, 脚底支撑和危险接触.
 * 水接触, 树叶支撑和下界顶层分别由三个配置项控制, 规则实例不可变, 可以复用到多次搜索.
 * 每次检查的区块来自 {@link SearchContext#chunks()}, 加载结果与本次搜索的其他规则共享.
 *
 * <p>规则检查输入位置的精确坐标, 脚底需要已经落在有效支撑面上.
 * 例如, 下半砖的顶面在方块底部上方 0.5 格处, 候选 Y 需要由选点方提供为这一高度.
 * 返回 false 后, 搜索调用方负责寻找下一处候选或处理 unsafe 回退.
 *
 * <pre>{@code
 * SafetyRule rule = PhysicalSafety.builder()
 *         .water(PhysicalSafety.WaterPolicy.HEAD_ABOVE_WATER)
 *         .leafSupport(true)
 *         .allowNetherRoof(false)
 *         .build();
 * CompoundTag encoded = SafetyRule.TYPES.encode(rule);
 * SafetyRule decoded = SafetyRule.TYPES.decode(encoded);
 * }</pre>
 *
 * @param water 身体与眼部接触水时采用的规则
 * @param leafSupport 是否允许树叶提供脚底支撑, 身体碰撞检查始终包含树叶
 * @param allowNetherRoof 是否允许进入有顶世界的逻辑高度上方, 仍受世界实际高度限制
 */
public record PhysicalSafety(@NotNull WaterPolicy water, boolean leafSupport, boolean allowNetherRoof) implements SafetyRule {
    static final String TYPE_ID = "physical-safety"; // 编译期常量, 注册表读取它时无需先初始化 PhysicalSafety
    public static final SparrowKey TYPE = SparrowKey.sparrow(TYPE_ID); // 内置物理安全规则的编解码类型
    private static final double COLLISION_EPSILON = 1.0E-7; // 身体边界的碰撞容差, 单位为方块
    private static final double SUPPORT_DEPTH = 1.0E-5;     // 脚底向下探测支撑的薄层厚度, 单位为方块

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    public static PhysicalSafety fromNbt(@NotNull CompoundTag tag) {
        // 显式的 false 是有效配置, 两个开关是否填写由字段是否存在判断.
        if (!tag.containsKey("leaf_support") || !tag.containsKey("allow_nether_roof")) {
            throw new IllegalArgumentException("Physical safety requires leaf support and nether roof settings");
        }
        return new PhysicalSafety(WaterPolicy.valueOf(tag.getString("water")), tag.getBoolean("leaf_support"), tag.getBoolean("allow_nether_roof"));
    }

    @NotNull
    @Override
    public SparrowKey type() {
        return TYPE;
    }

    @NotNull
    @Override
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("water", this.water.name());
        tag.putBoolean("leaf_support", this.leafSupport);
        tag.putBoolean("allow_nether_roof", this.allowNetherRoof);
        return tag;
    }

    /**
     * 检查候选能否容纳玩家身体, 是否有允许的脚底支撑, 以及是否接触危险地形或不允许的流体.
     * 所需区块按搜索环境的策略准备, 不允许生成且缺少区块时返回 false.
     *
     * @param context 本次搜索共用的运行环境, 沿搜索流程串行使用
     * @param candidate 精确候选坐标, <strong>必须有限且属于 context.world() 指定的世界</strong>
     * @return true 表示通过全部物理检查, false 表示当前候选不合格, 异常表示检查未能完成
     */
    @NotNull
    @Override
    public CompletableFuture<Boolean> test(@NotNull SearchContext context, @NotNull WorldLocation candidate) {
        Dimensions dimensions = context.dimensions();
        double halfWidth = dimensions.width() / 2;
        double halfDepth = dimensions.depth() / 2;
        AABB bounds = new AABB(
                candidate.x() - halfWidth,
                candidate.y(),
                candidate.z() - halfDepth,
                candidate.x() + halfWidth,
                candidate.y() + dimensions.height(),
                candidate.z() + halfDepth
        );
        // 世界范围检查只读取元数据, 超出范围的体积直接拒绝.
        if (!this.isWithinBounds(context, bounds)) return CompletableFuture.completedFuture(false);
        // 同次搜索的候选与规则共用区块准备结果, 只有可用时才读取方块.
        return context.chunks().prepare(dimensions, candidate.x(), candidate.z())
                .thenApply(available -> available && this.isSafe(context.chunks(), dimensions, candidate, bounds));
    }

    // 判断完整身体体积是否位于世界边界和允许的高度内.
    private boolean isWithinBounds(@NotNull SearchContext context, @NotNull AABB bounds) {
        SearchChunks chunks = context.chunks();
        ServerLevel world = ((CraftWorld) context.world()).getHandle();
        int maxY = chunks.getMinY() + chunks.getHeight();
        if (!this.allowNetherRoof && world.dimensionType().hasCeiling()) {
            maxY = Math.min(maxY, chunks.getMinY() + world.dimensionType().logicalHeight());
        }
        if (bounds.minY < chunks.getMinY() || bounds.maxY > maxY) return false;
        WorldBorder border = chunks.getWorldBorder();
        return bounds.minX >= border.getMinX() && bounds.maxX <= border.getMaxX() && bounds.minZ >= border.getMinZ() && bounds.maxZ <= border.getMaxZ();
    }

    /**
     * 在已准备的区块中检查身体, 支撑和危险接触.
     * 查询使用提交时的尺寸和原版通用方块碰撞形状, 不包含其他实体或装备带来的特殊通行能力.
     *
     * @param chunks 区块视图, <strong>身体及相邻碰撞范围必须已经准备成功</strong>
     * @param dimensions 玩家提交时的尺寸
     * @param candidate 当前精确候选坐标
     * @param bounds 位于世界允许范围内的完整身体体积
     * @return 所有物理检查均通过时为 true
     */
    private boolean isSafe(@NotNull SearchChunks chunks, @NotNull Dimensions dimensions, @NotNull WorldLocation candidate, @NotNull AABB bounds) {
        // 略微收缩身体边界, 与地板或天花板恰好贴合的接触允许通过.
        AABB body = bounds.deflate(COLLISION_EPSILON);
        if (chunks.getBlockCollisions(null, body).iterator().hasNext()) return false;
        // 支撑薄层保留精确脚底高度, 检查当前脚底下方实际相交的形状.
        AABB support = new AABB(body.minX, bounds.minY - SUPPORT_DEPTH, body.minZ, body.maxX, bounds.minY, body.maxZ);
        if (!this.hasSafeSupport(chunks, support)) return false;
        // 可变坐标只属于这一次检查, 规则实例本身可以在其他搜索中复用.
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        if (this.hasUnsafeContact(chunks, body, position)) return false;
        return this.isEyeAboveWater(chunks, candidate.x(), candidate.y() + dimensions.eyeHeight(), candidate.z(), position);
    }

    // 判断脚底是否接触至少一个允许的支撑面, 同时排除接触到的危险支撑面.
    private boolean hasSafeSupport(@NotNull SearchChunks chunks, @NotNull AABB support) {
        BlockCollisions<BlockState> collisions = new BlockCollisions<>(
                chunks,
                CollisionContext.empty(),
                support,
                false,
                (position, shape) -> chunks.getBlockState(position)
        );
        boolean supported = false;
        // 原版遍历保留形状所属的方块位置, 半砖和伸出所属格的形状也按真实接触判断.
        while (collisions.hasNext()) {
            BlockState state = collisions.next();
            if (this.isHazardous(state)) return false;
            if (this.leafSupport || !(state.getBlock() instanceof LeavesBlock)) {
                supported = true;
            }
        }
        return supported;
    }

    // 检查身体接触的危险方块和不允许的流体.
    private boolean hasUnsafeContact(@NotNull SearchChunks chunks, @NotNull AABB body, @NotNull BlockPos.MutableBlockPos position) {
        int minX = Mth.floor(body.minX);
        int maxX = Mth.floor(body.maxX);
        int minY = Mth.floor(body.minY);
        int maxY = Mth.floor(body.maxY);
        int minZ = Mth.floor(body.minZ);
        int maxZ = Mth.floor(body.maxZ);
        // 同一次方块读取供危险类型和流体判断使用.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    position.set(x, y, z);
                    BlockState state = chunks.getBlockState(position);
                    if (this.isHazardous(state) && body.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) return true;
                    FluidState fluid = state.getFluidState();
                    if (fluid.isEmpty() || (this.water == WaterPolicy.HEAD_ABOVE_WATER && fluid.is(FluidTags.WATER))) continue;
                    if (body.intersects(x, y, z, x + 1.0, y + fluid.getHeight(chunks, position), z + 1.0)) return true;
                }
            }
        }
        return false;
    }

    // 检查眼部是否露出水面, 眼部恰好位于水面时也视为接触水.
    private boolean isEyeAboveWater(@NotNull SearchChunks chunks, double x, double eyeY, double z, @NotNull BlockPos.MutableBlockPos position) {
        position.set(Mth.floor(x), Mth.floor(eyeY), Mth.floor(z));
        FluidState fluid = chunks.getFluidState(position);
        return !fluid.is(FluidTags.WATER) || eyeY > position.getY() + fluid.getHeight(chunks, position);
    }

    // 判断方块是否是危险方块.
    private boolean isHazardous(@NotNull BlockState state) {
        Block block = state.getBlock();
        return block instanceof BaseFireBlock
                || block instanceof CampfireBlock && state.getValue(BlockStateProperties.LIT)
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS
                || block == Blocks.POWDER_SNOW
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.WITHER_ROSE
                || block == Blocks.POINTED_DRIPSTONE
                || block == Blocks.NETHER_PORTAL
                || block == Blocks.END_PORTAL
                || block == Blocks.END_GATEWAY;
    }

    public static final class Builder {
        private WaterPolicy water = WaterPolicy.DISALLOW; // 默认要求身体与水分离
        private boolean leafSupport = false;              // 默认不把树叶计入有效支撑
        private boolean allowNetherRoof = false;          // 默认限制在有顶世界的逻辑高度内

        private Builder() {
        }

        /**
         * 指定候选位置允许怎样接触水.
         *
         * @param water 水接触规则, 覆盖默认的 DISALLOW
         * @return 此构建器
         */
        @NotNull
        public Builder water(@NotNull WaterPolicy water) {
            this.water = water;
            return this;
        }

        /**
         * 指定树叶能否成为候选位置的脚底支撑.
         * 树叶与身体的接触由碰撞检查判定.
         *
         * @param leafSupport true 表示允许树叶支撑, false 表示树叶不计入有效支撑
         * @return 此构建器
         */
        @NotNull
        public Builder leafSupport(boolean leafSupport) {
            this.leafSupport = leafSupport;
            return this;
        }

        /**
         * 指定下界顶层区域能否进入安全候选的允许范围.
         *
         * @param allowNetherRoof true 表示允许顶层区域, false 表示排除顶层区域
         * @return 此构建器
         */
        @NotNull
        public Builder allowNetherRoof(boolean allowNetherRoof) {
            this.allowNetherRoof = allowNetherRoof;
            return this;
        }

        @NotNull
        public PhysicalSafety build() {
            return new PhysicalSafety(this.water, this.leafSupport, this.allowNetherRoof);
        }
    }

    public enum WaterPolicy {
        HEAD_ABOVE_WATER, // 允许身体接触水, 要求眼部高于实际水面
        DISALLOW         // 要求身体检测体积与水分离
    }
}
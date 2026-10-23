package net.momirealms.sparrow.teleport.position.rtp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.search.SafetyRule;
import net.momirealms.sparrow.teleport.search.SearchContext;
import net.momirealms.sparrow.util.Dimensions;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 在已经选好的 X/Z 列中寻找一处落脚点, 再检查本次选择的安全规则.
 * 先逐条执行 testBeforeResolve, 全部没有拒绝后才准备区块.
 * <p><strong>每个实例只调用一次 find, 各列沿同一条搜索流程串行执行.</strong>
 */
final class RtpColumnSearch {
    private static final double COLLISION_EPSILON = 1.0E-7; // 脚底探测时使用的碰撞容差
    private static final double FOOT_THICKNESS = 1.0E-5;    // 用薄层模拟脚底, 单位为方块

    private final AreaSampler.Column column;           // 抽样器提供的世界, X/Z 和各矩形允许的高度
    private final SearchContext context;               // 本世界共用的区块缓存与玩家尺寸
    private final RTPPos options;                      // 本轮固定使用的扫描选项和规则
    private final SearchBudget budget;                 // 随机重抽和附近搜索共用的计数与时限
    private final RTPPos.ScanMode mode;                // 已按当前世界展开 AUTO 的实际扫描方式
    private final List<AreaSampler.HeightRange> heights; // 本列允许的高度与附近搜索限制的交集
    private final boolean[] deferred;                  // 当前列哪些规则需要在得到 Y 后继续检查
    private final Executor executor = SparrowPlugin.instance().scheduler().async(); // 异步结果返回后在工作线程继续
    private final CompletableFuture<Result> completion = new CompletableFuture<>(); // 本列结果, 同时作为取消标记
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(); // 当前读取的方块位置

    private Phase phase = Phase.BEFORE_RULES; // 当前执行前置规则, 加载区块, 扫描或完整坐标规则
    private int ruleIndex;                  // 当前阶段下一条要执行的规则
    private int rangeIndex;                 // 下一个要扫描的允许高度区间
    private int y;                          // 下一层要读取的方块 Y
    private int limit;                      // 当前高度区间最后要读取的方块 Y
    private int surfaceTop;                 // 高度图记录的最高非空气方块上方一格, 受世界高度限制
    private boolean rangeStarted;          // 是否已经开始扫描某个允许高度区间
    private boolean open;                  // 向下扫描是否已经找到空隙, 找到后才考虑下面的完整方块
    private @Nullable WorldLocation candidate; // 当前接受完整坐标规则检查的落脚点

    /**
     * 创建随机起点的列搜索, 使用这一列的全部允许高度.
     *
     * @param column 抽样器返回的列
     * @param options 本轮 RTP 配置
     * @param budget 本轮共用的预算, 随机尝试次数已经由抽样器计入
     */
    RtpColumnSearch(@NotNull AreaSampler.Column column, @NotNull RTPPos options, @NotNull SearchBudget budget) {
        this(column, options, budget, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    /**
     * 创建带额外高度限制的列搜索, 供附近搜索使用.
     * 例如起点脚底在 64, 允许上下各 4 格, 就传入 60 和 68.
     * 本列仍须遵守覆盖它的各个 Rectangle 的高度范围.
     *
     * @param column 附近坐标向 AreaSampler 查询得到的列
     * @param options 本轮 RTP 配置
     * @param budget 本轮共用的预算, 附近列不增加随机尝试次数
     * @param minY 额外的脚底高度下界, 随机起点可用负无穷表示不额外限制
     * @param maxY 额外的脚底高度上界, 随机起点可用正无穷表示不额外限制
     */
    RtpColumnSearch(@NotNull AreaSampler.Column column, @NotNull RTPPos options, @NotNull SearchBudget budget, double minY, double maxY) {
        this.column = column;
        this.context = column.context();
        this.options = options;
        this.budget = budget;
        this.deferred = new boolean[options.rules().size()];
        ServerLevel world = ((CraftWorld) this.context.world()).getHandle();
        boolean ceiling = world.dimensionType().hasCeiling();
        this.mode = options.scanMode() == RTPPos.ScanMode.AUTO
                ? (ceiling ? RTPPos.ScanMode.CAVITY_TOP_DOWN : RTPPos.ScanMode.SURFACE)
                : options.scanMode();
        // 下界从逻辑顶层下面找空腔, 候选还要给完整身体留出高度.
        if (this.mode == RTPPos.ScanMode.CAVITY_TOP_DOWN && ceiling) {
            maxY = Math.min(maxY, this.context.chunks().getMinY() + world.dimensionType().logicalHeight() - this.context.dimensions().height());
        }
        this.heights = this.intersectHeights(minY, maxY);
    }

    /**
     * 给各段允许高度套上本次列搜索的额外限制, 保留区间之间的空档.
     *
     * @param minY 额外下界
     * @param maxY 额外上界
     * @return 从低到高排列的不可变高度列表, 没有交集时为空
     */
    @NotNull
    private List<AreaSampler.HeightRange> intersectHeights(double minY, double maxY) {
        List<AreaSampler.HeightRange> result = new ArrayList<>();
        for (int index = 0, size = this.column.heights().size(); index < size && this.budget.active(); index++) {
            AreaSampler.HeightRange range = this.column.heights().get(index);
            double lower = Math.max(range.minY(), minY);
            double upper = Math.min(range.maxY(), maxY);
            if (lower <= upper) {
                result.add(new AreaSampler.HeightRange(lower, upper));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 进入这一列并开始检查, 先消耗一次列检查额度.
     * 整轮时限由 RtpSearch 计时, 整轮结束时取消本列的 Future.
     *
     * @return 本列的结果, 异常保留为异常完成, 调用方可以取消此 Future
     */
    @NotNull
    CompletableFuture<Result> find() {
        if (this.heights.isEmpty() || !this.budget.tryCheck()) {
            this.completion.complete(Result.Miss.INSTANCE);
            return this.completion;
        }
        this.advance();
        return this.completion;
    }

    /**
     * 连续执行已经能完成的步骤, 需要等待区块或规则时交出执行权.
     * 外部规则可以同步抛异常, 也可以让 Future 异常完成, 两种情况都结束本列并向上传播.
     */
    private void advance() {
        try {
            while (this.canContinue()) {
                switch (this.phase) {
                    case BEFORE_RULES -> {
                        if (this.ruleIndex == this.options.rules().size()) {
                            this.phase = Phase.LOAD;
                            continue;
                        }
                        int index = this.ruleIndex++;
                        CompletableFuture<SafetyRule.BeforeResult> checked = this.options.rules().get(index)
                                .testBeforeResolve(this.context, this.column.blockX(), this.column.blockZ());
                        if (this.await(checked, result -> this.applyBefore(index, result))) return;
                    }
                    case LOAD -> {
                        this.phase = Phase.SCAN;
                        CompletableFuture<Boolean> ready = this.context.chunks().prepare(this.context.dimensions(), this.column.blockX() + 0.5, this.column.blockZ() + 0.5);
                        if (this.await(ready, available -> {
                            if (available) {
                                this.beginScan();
                            } else {
                                this.completion.complete(Result.Miss.INSTANCE);
                            }
                        })) return;
                    }
                    case SCAN -> {
                        if (!this.selectLayer() || !this.budget.tryCheck()) {
                            this.completion.complete(Result.Miss.INSTANCE);
                            return;
                        }
                        int blockY = this.y;
                        this.y += this.mode == RTPPos.ScanMode.CAVITY_BOTTOM_UP ? 1 : -1;
                        this.scanLayer(blockY);
                    }
                    case CANDIDATE_RULES -> {
                        while (this.ruleIndex < this.deferred.length && !this.deferred[this.ruleIndex]) {
                            this.ruleIndex++;
                        }
                        if (this.ruleIndex == this.deferred.length) {
                            this.completion.complete(new Result.Found(this.candidate));
                            return;
                        }
                        CompletableFuture<Boolean> checked = this.options.rules().get(this.ruleIndex++).test(this.context, this.candidate);
                        if (this.await(checked, accepted -> {
                            if (!accepted) this.completion.complete(new Result.Rejected(this.candidate));
                        })) return;
                    }
                }
            }
        } catch (Throwable error) {
            this.completion.completeExceptionally(error);
        }
    }

    /**
     * 保存本列的前置检查结果. PASS 的规则无需再检查完整坐标, REJECT 直接结束本列.
     *
     * @param index 当前规则在配置中的下标
     * @param result 当前规则的前置结果
     */
    private void applyBefore(int index, @NotNull SafetyRule.BeforeResult result) {
        switch (result) {
            case PASS -> {
            }
            case REJECT -> this.completion.complete(Result.Miss.INSTANCE);
            case DEFER -> this.deferred[index] = true;
        }
    }

    /**
     * 接收一个步骤的结果, 同步完成时直接继续, 异步完成时回到工作线程继续.
     * 回调开始时检查取消和时限, 已结束搜索的迟到结果不会启动下一步.
     *
     * @param future 区块准备或外部规则的结果
     * @param action 正常结果需要更新的本列状态
     * @param <T> 当前步骤的结果类型
     * @return true 表示已经挂接异步回调, 当前推进过程应当返回
     */
    private <T> boolean await(@NotNull CompletableFuture<T> future, @NotNull Consumer<T> action) {
        if (future.isDone()) {
            if (this.canContinue()) action.accept(future.join());
            return false;
        }
        future.thenAcceptAsync(value -> {
            if (!this.canContinue()) return;
            action.accept(value);
            this.advance();
        }, this.executor).whenComplete((ignored, error) -> {
            if (error != null && this.canContinue()) this.completion.completeExceptionally(error);
        });
        return true;
    }

    /**
     * 判断本列能否继续, 只检查停止和时限, 已扣除的最后一次检查仍可执行完整规则链.
     *
     * @return 本列尚未结束且整轮预算仍在时限内时为 true
     */
    private boolean canContinue() {
        if (this.completion.isDone()) return false;
        if (this.budget.active()) return true;
        this.completion.complete(Result.Miss.INSTANCE);
        return false;
    }

    /**
     * 区块就绪后确定扫描方向和起点.
     * 高度图使用最高非空气方块, 保留树叶和流体, 后面的扫描才能按配置逐一处理它们.
     */
    private void beginScan() {
        int surface = this.context.chunks().getChunkForCollisions(this.column.blockX() >> 4, this.column.blockZ() >> 4)
                .getHeight(Heightmap.Types.WORLD_SURFACE, this.column.blockX() & 15, this.column.blockZ() & 15);
        int worldTop = this.context.chunks().getMinY() + this.context.chunks().getHeight() - 1;
        this.surfaceTop = Math.min(worldTop, surface + 1);
        if (this.mode == RTPPos.ScanMode.SURFACE) {
            this.y = Math.min(worldTop, surface);
            this.limit = Math.max(this.context.chunks().getMinY(), Mth.floor(this.heights.getFirst().minY()) - 1);
            this.open = true;
        } else {
            boolean upward = this.mode == RTPPos.ScanMode.CAVITY_BOTTOM_UP;
            this.rangeIndex = upward ? 0 : this.heights.size() - 1;
            this.y = upward ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
    }

    /**
     * 选择下一层, 当前高度区间用完后跳到下一段允许高度.
     * 检查地板需要读取脚底下方一格, 向上搜索还要看到地板上方的空隙.
     * 不同高度区间的辅助读取范围可能重叠, 已经过的 Y 层不会重新扫描或计数.
     *
     * @return 还有一层可扫描时为 true
     */
    private boolean selectLayer() {
        if (this.mode == RTPPos.ScanMode.SURFACE) return this.y >= this.limit;
        boolean upward = this.mode == RTPPos.ScanMode.CAVITY_BOTTOM_UP;
        if (this.rangeStarted && (upward ? this.y <= this.limit : this.y >= this.limit)) return true;
        while (this.rangeIndex >= 0 && this.rangeIndex < this.heights.size() && this.canContinue()) {
            AreaSampler.HeightRange range = this.heights.get(this.rangeIndex);
            this.rangeIndex += upward ? 1 : -1;
            int lower = Math.max(this.context.chunks().getMinY(), Mth.floor(range.minY()) - 1);
            int upper = Math.min(this.surfaceTop, Mth.floor(range.maxY()) + (upward ? 1 : 0));
            int start = upward ? Math.max(lower, this.y) : Math.min(upper, this.y);
            if (upward ? start > upper : start < lower) continue;
            if (start != this.y) {
                this.open = false;
            }
            this.y = start;
            this.limit = upward ? upper : lower;
            this.rangeStarted = true;
            return true;
        }
        return false;
    }

    /**
     * 读取一层的流体和碰撞形状, 按方向寻找空隙与地板.
     * 向下时先穿过顶板, 看到空隙后才考虑下面的完整方块.
     * 向上时遇到空隙便检查它下面的地板, 半砖则可以在同一格内提供空隙和支撑.
     *
     * @param blockY 本次已经扣除检查额度的方块 Y
     */
    private void scanLayer(int blockY) {
        this.position.set(this.column.blockX(), blockY, this.column.blockZ());
        BlockState state = this.context.chunks().getBlockState(this.position);
        // 含水树叶也先处理流体, 再决定是否跳过树叶的碰撞.
        if (this.options.stopAtFluid() && !state.getFluidState().isEmpty()) {
            this.completion.complete(Result.Miss.INSTANCE);
            return;
        }
        boolean ignored = this.options.ignoreLeaves() && state.getBlock() instanceof LeavesBlock;
        VoxelShape shape = ignored ? Shapes.empty() : state.getCollisionShape(this.context.chunks(), this.position);
        if (this.mode == RTPPos.ScanMode.CAVITY_BOTTOM_UP) {
            if (shape.isEmpty()) {
                this.selectCandidate(this.getStandingY(blockY + 1.0 - COLLISION_EPSILON, blockY - 1.0));
            } else if (shape.max(Direction.Axis.Y) < 1.0) {
                this.selectCandidate(this.getStandingY(blockY + 1.0 - COLLISION_EPSILON, blockY));
            }
            return;
        }
        if (shape.isEmpty()) {
            this.open = true;
            return;
        }
        double shapeTop = shape.max(Direction.Axis.Y);
        if (!this.open && shapeTop >= 1.0) return;
        double standingY = this.getStandingY(blockY + Math.floor(shapeTop) + 1.0 - COLLISION_EPSILON, blockY);
        this.open = Double.isNaN(standingY);
        this.selectCandidate(standingY);
    }

    /**
     * 用玩家脚底大小的薄层向下移动, 取得最先碰到的支撑面高度.
     * 原版碰撞形状会保留半砖和栅栏的小数高度, 也会考虑相邻方块伸入脚底的部分.
     * ignoreLeaves 只影响这里的选点, 完整安全规则仍读取原始方块.
     *
     * @param top 薄层开始下降的高度
     * @param bottom 本次探测的最低高度, 必须小于 top
     * @return 碰到支撑面时的脚底高度, 没有碰到时为 NaN
     */
    private double getStandingY(double top, double bottom) {
        Dimensions dimensions = this.context.dimensions();
        double x = this.column.blockX() + 0.5;
        double z = this.column.blockZ() + 0.5;
        AABB feet = new AABB(
                x - dimensions.width() / 2 + COLLISION_EPSILON,
                top,
                z - dimensions.depth() / 2 + COLLISION_EPSILON,
                x + dimensions.width() / 2 - COLLISION_EPSILON,
                top + FOOT_THICKNESS,
                z + dimensions.depth() / 2 - COLLISION_EPSILON
        );
        double fullMovement = bottom - top;
        double movement = fullMovement;
        BlockCollisions<VoxelShape> collisions = new BlockCollisions<>(
                this.context.chunks(),
                CollisionContext.empty(),
                feet.expandTowards(0, fullMovement, 0),
                false,
                (position, shape) -> this.options.ignoreLeaves() && this.context.chunks().getBlockState(position).getBlock() instanceof LeavesBlock ? Shapes.empty() : shape
        );
        while (collisions.hasNext()) {
            movement = collisions.next().collide(Direction.Axis.Y, feet, movement);
        }
        return movement == fullMovement ? Double.NaN : top + movement;
    }

    /**
     * 只有脚底高度落在允许区间内时才启动完整坐标规则.
     * 地表模式找到的地面不在范围内时结束本列; 空腔模式继续按方向寻找范围内的地板.
     *
     * @param standingY 几何探测得到的脚底高度, NaN 表示没有支撑面
     */
    private void selectCandidate(double standingY) {
        if (Double.isNaN(standingY) || !this.canContinue()) return;
        for (int index = 0, size = this.heights.size(); index < size; index++) {
            AreaSampler.HeightRange range = this.heights.get(index);
            if (standingY >= range.minY() && standingY <= range.maxY()) {
                this.candidate = new WorldLocation(this.context.world().getName(), this.column.blockX() + 0.5, standingY, this.column.blockZ() + 0.5, 0, 0);
                this.ruleIndex = 0;
                this.phase = Phase.CANDIDATE_RULES;
                return;
            }
        }
        if (this.mode == RTPPos.ScanMode.SURFACE) {
            this.completion.complete(Result.Miss.INSTANCE);
        }
    }

    /**
     * 记录一次列搜索的结果, 供调用方决定采用, 搜索附近或重新抽列.
     */
    sealed interface Result {

        /**
         * 当前列没有可交给附近搜索的候选, 也可能是共享预算已经耗尽.
         */
        enum Miss implements Result {
            INSTANCE // 不携带候选坐标
        }

        /**
         * 候选已经通过全部所选规则.
         *
         * @param location 可以采用的精确落脚点
         */
        record Found(@NotNull WorldLocation location) implements Result {
        }

        /**
         * 已找到落脚面, 但完整坐标规则拒绝, 调用方可围绕此坐标搜索附近列.
         *
         * @param location 被拒绝的候选, 作为附近搜索的高度与水平原点
         */
        record Rejected(@NotNull WorldLocation location) implements Result {
        }
    }

    /**
     * 等待异步结果时保留执行进度, 回调完成后从当前阶段继续.
     */
    private enum Phase {
        BEFORE_RULES,   // 在读取区块前执行前置规则
        LOAD,           // 准备本列及脚底碰撞需要的区块
        SCAN,           // 按模式读取地形, 寻找落脚面
        CANDIDATE_RULES // 已经有完整坐标, 执行前置 DEFER 的规则
    }
}
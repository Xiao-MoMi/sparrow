package net.momirealms.sparrow.teleport.search;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.momirealms.sparrow.util.Dimensions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class TeleportSafety {
    private static final double COLLISION_EPSILON = 1.0E-7;
    private static final double SUPPORT_DEPTH = 1.0E-5;

    private TeleportSafety() {
    }

    // 调用方需预先加载检测体积及相邻一格覆盖的区块, 碰撞形状可能延伸到所属方块之外
    public static boolean isSafe(@NotNull ServerLevel world, @NotNull Dimensions dimensions, double x, double y, double z, boolean allowNetherRoof) {
        return isSafe(world, dimensions, x, y, z, getMaxY(world, allowNetherRoof));
    }

    public static int getMaxY(@NotNull ServerLevel world, boolean allowNetherRoof) {
        int maxY = world.getMinY() + world.getHeight();
        if (!allowNetherRoof && world.dimensionType().hasCeiling()) {
            maxY = Math.min(maxY, world.getMinY() + world.dimensionType().logicalHeight());
        }
        return maxY;
    }

    public static boolean isSafe(@NotNull CollisionGetter world, @NotNull Dimensions dimensions, double x, double y, double z, int maxY) {
        return isSafe(world, dimensions, x, y, z, maxY, new BlockPos.MutableBlockPos());
    }

    @ApiStatus.Internal
    public static boolean isSafe(
            @NotNull CollisionGetter world,
            @NotNull Dimensions dimensions,
            double x,
            double y,
            double z,
            int maxY,
            @NotNull BlockPos.MutableBlockPos position
    ) {
        double halfWidth = dimensions.width() / 2;
        double halfDepth = dimensions.depth() / 2;
        AABB bounds = new AABB(x - halfWidth, y, z - halfDepth, x + halfWidth, y + dimensions.height(), z + halfDepth);
        if (!isWithinBounds(world, bounds, maxY)) return false;
        // 身体与支撑面共用原版容差, 接触检查复用同一组检测体积
        AABB body = getBodyBounds(bounds);
        if (hasBodyCollision(world, body, position)) return false;
        AABB support = getSupportBounds(bounds, body);
        if (!hasSupport(world, support)) return false;
        if (hasHazards(world, body, support, position)) return false;
        return !isEyeInWater(world, x, y + dimensions.eyeHeight(), z, position);
    }

    // 从候选格顶面向下探测一格, 返回检测体积脚下最高支撑面的 Y, 无支撑时返回候选格底面
    public static double getStandingY(@NotNull CollisionGetter world, @NotNull Dimensions dimensions, double x, int y, double z) {
        double halfWidth = dimensions.width() / 2;
        double halfDepth = dimensions.depth() / 2;
        double top = y + 1.0 - COLLISION_EPSILON;
        AABB feet = new AABB(
                x - halfWidth + COLLISION_EPSILON,
                top,
                z - halfDepth + COLLISION_EPSILON,
                x + halfWidth - COLLISION_EPSILON,
                top + SUPPORT_DEPTH,
                z + halfDepth - COLLISION_EPSILON
        );
        double movement = y - top;
        for (VoxelShape shape : world.getBlockCollisions(null, feet.expandTowards(0, -1, 0))) {
            movement = shape.collide(Direction.Axis.Y, feet, movement);
        }
        return Math.max(y, top + movement);
    }

    @ApiStatus.Internal
    public static boolean isWithinBounds(@NotNull CollisionGetter world, @NotNull AABB bounds, int maxY) {
        if (bounds.minY < world.getMinY() || bounds.maxY > maxY) return false;
        WorldBorder border = world.getWorldBorder();
        if (bounds.minX < border.getMinX() || bounds.maxX > border.getMaxX() || bounds.minZ < border.getMinZ() || bounds.maxZ > border.getMaxZ()) return false;
        return true;
    }

    @NotNull
    @ApiStatus.Internal
    public static AABB getBodyBounds(@NotNull AABB bounds) {
        return bounds.deflate(COLLISION_EPSILON);
    }

    // body 由 getBodyBounds 生成, 包含贴合地板和天花板所需的容差
    @ApiStatus.Internal
    public static boolean hasBodyCollision(@NotNull CollisionGetter world, @NotNull AABB body, @NotNull BlockPos.MutableBlockPos position) {
        position.set(Mth.floor((body.minX + body.maxX) / 2), Mth.floor((body.minY + body.maxY) / 2), Mth.floor((body.minZ + body.maxZ) / 2));
        if (world.getBlockState(position).isCollisionShapeFullBlock(world, position)
                && body.intersects(position.getX(), position.getY(), position.getZ(), position.getX() + 1.0, position.getY() + 1.0, position.getZ() + 1.0)) return true;
        return world.getBlockCollisions(null, body).iterator().hasNext();
    }

    // bounds 保留原始脚底高度, body 提供经过容差收缩的水平范围
    @NotNull
    @ApiStatus.Internal
    public static AABB getSupportBounds(@NotNull AABB bounds, @NotNull AABB body) {
        return new AABB(body.minX, bounds.minY - SUPPORT_DEPTH, body.minZ, body.maxX, bounds.minY, body.maxZ);
    }

    @ApiStatus.Internal
    public static boolean hasSupport(@NotNull CollisionGetter world, @NotNull AABB support) {
        return world.getBlockCollisions(null, support).iterator().hasNext();
    }

    @ApiStatus.Internal
    public static boolean isEyeInWater(@NotNull CollisionGetter world, double x, double eyeY, double z, @NotNull BlockPos.MutableBlockPos position) {
        position.set(Mth.floor(x), Mth.floor(eyeY), Mth.floor(z));
        FluidState fluid = world.getFluidState(position);
        return fluid.is(FluidTags.WATER) && eyeY <= position.getY() + fluid.getHeight(world, position);
    }

    // 危险方块和流体共用一次方块遍历, 每次读取的状态供两项检查复用
    @ApiStatus.Internal
    public static boolean hasHazards(@NotNull CollisionGetter world, @NotNull AABB body, @NotNull AABB support, @NotNull BlockPos.MutableBlockPos position) {
        int minX = Mth.floor(body.minX);
        int maxX = Mth.floor(body.maxX);
        int minY = Mth.floor(support.minY);
        int maxY = Mth.floor(body.maxY);
        int minZ = Mth.floor(body.minZ);
        int maxZ = Mth.floor(body.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    position.set(x, y, z);
                    BlockState state = world.getBlockState(position);
                    if (touchesHazardousBlock(world, state, body, support, position)) return true;
                    if (touchesUnsafeFluid(world, state.getFluidState(), body, position)) return true;
                }
            }
        }
        return false;
    }

    @ApiStatus.Internal
    public static boolean touchesHazardousBlock(
            @NotNull CollisionGetter world,
            @NotNull BlockState state,
            @NotNull AABB body,
            @NotNull AABB support,
            @NotNull BlockPos position
    ) {
        if (!isHazardous(state)) return false;
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        if (body.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) return true;
        return Shapes.joinIsNotEmpty(state.getCollisionShape(world, position).move(position), Shapes.create(support), BooleanOp.AND);
    }

    // 水允许接触身体, 其他流体按实际液面检查交叠
    @ApiStatus.Internal
    public static boolean touchesUnsafeFluid(@NotNull CollisionGetter world, @NotNull FluidState fluid, @NotNull AABB body, @NotNull BlockPos position) {
        if (fluid.isEmpty() || fluid.is(FluidTags.WATER)) return false;
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        return body.intersects(x, y, z, x + 1.0, y + fluid.getHeight(world, position), z + 1.0);
    }

    private static boolean isHazardous(BlockState state) {
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
}
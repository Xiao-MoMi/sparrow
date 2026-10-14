package net.momirealms.sparrow.util;

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
import net.momirealms.sparrow.teleport.TeleportContext.Dimensions;
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
        if (!isSafe(world, bounds, maxY, position)) return false;
        double eyeY = y + dimensions.eyeHeight();
        position.set(Mth.floor(x), Mth.floor(eyeY), Mth.floor(z));
        FluidState fluid = world.getFluidState(position);
        return !fluid.is(FluidTags.WATER) || eyeY > position.getY() + fluid.getHeight(world, position);
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

    private static boolean isSafe(CollisionGetter world, AABB bounds, int maxY, BlockPos.MutableBlockPos position) {
        if (bounds.minY < world.getMinY() || bounds.maxY > maxY) return false;
        WorldBorder border = world.getWorldBorder();
        if (bounds.minX < border.getMinX() || bounds.maxX > border.getMaxX() || bounds.minZ < border.getMinZ() || bounds.maxZ > border.getMaxZ()) return false;
        // 使用原版容差处理贴合的地板和天花板, 身体必须完全处于方块碰撞形状之外
        AABB body = bounds.deflate(COLLISION_EPSILON);
        position.set(Mth.floor((body.minX + body.maxX) / 2), Mth.floor((body.minY + body.maxY) / 2), Mth.floor((body.minZ + body.maxZ) / 2));
        if (world.getBlockState(position).isCollisionShapeFullBlock(world, position)
                && body.intersects(position.getX(), position.getY(), position.getZ(), position.getX() + 1.0, position.getY() + 1.0, position.getZ() + 1.0)) return false;
        if (world.getBlockCollisions(null, body).iterator().hasNext()) return false;
        // 脚底向下探测一个薄层, 半砖, 楼梯和栅栏都按实际碰撞面提供支撑
        AABB support = new AABB(body.minX, bounds.minY - SUPPORT_DEPTH, body.minZ, body.maxX, bounds.minY, body.maxZ);
        if (!world.getBlockCollisions(null, support).iterator().hasNext()) return false;
        return !hasHazards(world, body, support, position);
    }

    private static boolean hasHazards(CollisionGetter world, AABB body, AABB support, BlockPos.MutableBlockPos position) {
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
                    if (isHazardous(state)) {
                        if (body.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) return true;
                        if (Shapes.joinIsNotEmpty(state.getCollisionShape(world, position).move(position), Shapes.create(support), BooleanOp.AND)) return true;
                    }
                    FluidState fluid = state.getFluidState();
                    // 水允许接触身体, 其他流体按实际液面检查交叠
                    if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER) && body.intersects(x, y, z, x + 1.0, y + fluid.getHeight(world, position), z + 1.0)) return true;
                }
            }
        }
        return false;
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
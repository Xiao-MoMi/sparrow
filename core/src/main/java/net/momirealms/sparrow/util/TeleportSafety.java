package net.momirealms.sparrow.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.momirealms.sparrow.teleport.TeleportDimensions;
import org.jetbrains.annotations.NotNull;

public final class TeleportSafety {
    private static final double COLLISION_EPSILON = 1.0E-7;
    private static final double SUPPORT_DEPTH = 1.0E-5;

    private TeleportSafety() {
    }

    // 调用方需预先加载检测体积及相邻一格覆盖的区块, 碰撞形状可能延伸到所属方块之外
    public static boolean isSafe(@NotNull ServerLevel world, @NotNull TeleportDimensions dimensions, double x, double y, double z, boolean allowNetherRoof) {
        int maxY = world.getMinY() + world.getHeight();
        if (!allowNetherRoof && world.dimensionType().hasCeiling()) {
            maxY = Math.min(maxY, world.getMinY() + world.dimensionType().logicalHeight());
        }
        double halfWidth = dimensions.width() / 2;
        double halfDepth = dimensions.depth() / 2;
        AABB bounds = new AABB(x - halfWidth, y, z - halfDepth, x + halfWidth, y + dimensions.height(), z + halfDepth);
        return isSafe(world, bounds, maxY);
    }

    private static boolean isSafe(CollisionGetter world, AABB bounds, int maxY) {
        if (bounds.minY < world.getMinY() || bounds.maxY > maxY) return false;
        WorldBorder border = world.getWorldBorder();
        if (bounds.minX < border.getMinX() || bounds.maxX > border.getMaxX() || bounds.minZ < border.getMinZ() || bounds.maxZ > border.getMaxZ()) return false;
        // 使用原版容差处理贴合的地板和天花板, 身体必须完全处于方块碰撞形状之外
        AABB body = bounds.deflate(COLLISION_EPSILON);
        if (world.getBlockCollisions(null, body).iterator().hasNext()) return false;
        // 脚底向下探测一个薄层, 半砖, 楼梯和栅栏都按实际碰撞面提供支撑
        AABB support = new AABB(body.minX, bounds.minY - SUPPORT_DEPTH, body.minZ, body.maxX, bounds.minY, body.maxZ);
        if (!world.getBlockCollisions(null, support).iterator().hasNext()) return false;
        return !hasHazards(world, body, support);
    }

    private static boolean hasHazards(CollisionGetter world, AABB body, AABB support) {
        int minX = Mth.floor(body.minX);
        int maxX = Mth.floor(body.maxX);
        int minY = Mth.floor(support.minY);
        int maxY = Mth.floor(body.maxY);
        int minZ = Mth.floor(body.minZ);
        int maxZ = Mth.floor(body.maxZ);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
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
                    // 流体只检查身体与实际液面的交叠, 含水方块的干燥顶面仍可站立
                    if (!fluid.isEmpty() && body.intersects(x, y, z, x + 1.0, y + fluid.getHeight(world, position), z + 1.0)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isHazardous(BlockState state) {
        return state.getBlock() instanceof BaseFireBlock
                || state.getBlock() instanceof CampfireBlock && state.getValue(BlockStateProperties.LIT)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY);
    }
}
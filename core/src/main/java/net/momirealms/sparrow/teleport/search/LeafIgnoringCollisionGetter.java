package net.momirealms.sparrow.teleport.search;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public record LeafIgnoringCollisionGetter(@NotNull CollisionGetter delegate) implements CollisionGetter {

    @Nullable
    @Override
    public BlockGetter getChunkForCollisions(int x, int z) {
        // 原版碰撞迭代器会从区块读取方块, 这里也必须经过树叶转换
        return this.delegate.getChunkForCollisions(x, z) == null ? null : this;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        BlockState state = this.delegate.getBlockState(position);
        return state.getBlock() instanceof LeavesBlock ? state.getFluidState().createLegacyBlock() : state;
    }

    @Nullable
    @Override
    public BlockState getBlockStateIfLoaded(BlockPos position) {
        BlockState state = this.delegate.getBlockStateIfLoaded(position);
        return state != null && state.getBlock() instanceof LeavesBlock ? state.getFluidState().createLegacyBlock() : state;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return this.delegate.getFluidState(position);
    }

    @Nullable
    @Override
    public FluidState getFluidIfLoaded(BlockPos position) {
        return this.delegate.getFluidIfLoaded(position);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return this.delegate.getBlockEntity(position);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.delegate.getWorldBorder();
    }

    @Override
    public int getMinY() {
        return this.delegate.getMinY();
    }

    @Override
    public int getHeight() {
        return this.delegate.getHeight();
    }

    @NotNull
    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB bounds) {
        return this.delegate.getEntityCollisions(entity, bounds);
    }
}
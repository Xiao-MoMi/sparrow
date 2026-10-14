package net.momirealms.sparrow.teleport.search;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CollisionGetter;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.teleport.TeleportDimensions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class CandidateContext {
    private final SparrowPlayer player;
    private final TeleportDimensions dimensions;
    private final CollisionGetter blocks;
    private final int maxY;
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(); // 内置安全规则在本次搜索的执行器上复用

    @ApiStatus.Internal
    public CandidateContext(@NotNull SparrowPlayer player, @NotNull TeleportDimensions dimensions, @NotNull CollisionGetter blocks, int maxY) {
        this.player = player;
        this.dimensions = dimensions;
        this.blocks = blocks;
        this.maxY = maxY;
    }

    @NotNull
    public SparrowPlayer player() {
        return this.player;
    }

    @NotNull
    public TeleportDimensions dimensions() {
        return this.dimensions;
    }

    @NotNull
    @ApiStatus.Internal
    public CollisionGetter blocks() {
        return this.blocks;
    }

    @ApiStatus.Internal
    public int maxY() {
        return this.maxY;
    }

    @NotNull
    @ApiStatus.Internal
    public BlockPos.MutableBlockPos position() {
        return this.position;
    }
}
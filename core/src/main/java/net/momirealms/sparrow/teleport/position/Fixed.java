package net.momirealms.sparrow.teleport.position;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

record Fixed(@NotNull WorldLocation location) implements TeleportPosition {
    static final SparrowKey TYPE = SparrowKey.sparrow("fixed");

    @NotNull
    static Fixed fromNbt(@NotNull CompoundTag tag) {
        return new Fixed(WorldLocation.fromNbt(tag));
    }

    @NotNull
    @Override
    public SparrowKey type() {
        return TYPE;
    }

    @NotNull
    @Override
    public CompoundTag toNbt() {
        return this.location.toNbt();
    }

    @NotNull
    @Override
    public CompletableFuture<Result> resolve(@NotNull TeleportContext context) {
        return CompletableFuture.completedFuture(new Result.Success(this.location));
    }
}
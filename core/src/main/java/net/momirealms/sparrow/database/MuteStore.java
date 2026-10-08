package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.mute.MuteRecord;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MuteStore {

    @NotNull
    CompletableFuture<Void> initialize();

    @NotNull
    CompletableFuture<Optional<MuteRecord>> findActive(@NotNull UUID player, long now);

    @NotNull
    CompletableFuture<Boolean> create(@NotNull MuteRecord record);

    @NotNull
    CompletableFuture<Optional<MuteRecord>> revoke(@NotNull UUID player, long now, @NotNull String operator);
}
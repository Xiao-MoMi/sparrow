package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.spawn.Spawn;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SpawnStore {

    @NotNull
    CompletableFuture<Void> initialize();

    @NotNull
    CompletableFuture<Optional<Spawn>> load();

    @NotNull
    CompletableFuture<Void> save(@NotNull Spawn spawn);

    @NotNull
    CompletableFuture<Boolean> delete();
}
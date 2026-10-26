package net.momirealms.sparrow.database;

import net.momirealms.sparrow.util.ServerLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BackStore {

    @NotNull
    CompletableFuture<Optional<ServerLocation>> loadBack(@NotNull UUID player);

    @NotNull
    CompletableFuture<Void> saveBack(@NotNull UUID player, @NotNull ServerLocation location);

    @NotNull
    CompletableFuture<Optional<ServerLocation>> loadDeathBack(@NotNull UUID player);

    @NotNull
    CompletableFuture<Void> saveDeathBack(@NotNull UUID player, @NotNull ServerLocation location);
}
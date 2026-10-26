package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WarpStore {

    @NotNull
    CompletableFuture<List<Warp>> loadAll();

    @NotNull
    CompletableFuture<Optional<Warp>> find(@NotNull UUID id);

    @NotNull
    CompletableFuture<Status> create(@NotNull Warp warp);

    @NotNull
    CompletableFuture<Status> rename(@NotNull UUID id, @NotNull String name, long updatedAt);

    @NotNull
    CompletableFuture<Status> relocate(@NotNull UUID id, @NotNull String server, @NotNull WorldLocation location, long updatedAt);

    @NotNull
    CompletableFuture<Boolean> delete(@NotNull UUID id);

    enum Status {
        SUCCESS, DUPLICATE_NAME, NOT_FOUND
    }
}
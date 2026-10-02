package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.warp.Warp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WarpStore {

    @NotNull
    CompletableFuture<Void> initialize();

    @NotNull
    CompletableFuture<List<Warp>> loadAll();

    @NotNull
    CompletableFuture<Optional<Warp>> find(@NotNull UUID id);

    @NotNull
    CompletableFuture<Optional<Warp>> findByName(@NotNull String nameIgnoreCase);

    @NotNull
    CompletableFuture<SaveResult> create(@NotNull Warp warp);

    @NotNull
    CompletableFuture<SaveResult> update(@NotNull Warp warp);

    @NotNull
    CompletableFuture<Boolean> delete(@NotNull UUID id);

    @NotNull
    CompletableFuture<Integer> deleteByWorld(@NotNull String server, @NotNull String world);

    @NotNull
    CompletableFuture<Integer> deleteByServer(@NotNull String server);

    enum Status {
        SUCCESS, DUPLICATE_NAME, NOT_FOUND
    }

    // warp 仅在成功时存在, 包含实际保存的创建信息.
    record SaveResult(@NotNull Status status, @Nullable Warp warp) {
    }
}

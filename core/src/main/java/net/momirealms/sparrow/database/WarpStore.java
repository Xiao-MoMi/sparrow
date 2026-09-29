package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.warp.Warp;
import org.jetbrains.annotations.NotNull;

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

    // 两台服务器同时写入同一个新名称时, 后写入的一方由唯一索引拒绝, 任务以异常结束.
    @NotNull
    CompletableFuture<Boolean> save(@NotNull Warp warp);

    @NotNull
    CompletableFuture<Boolean> delete(@NotNull UUID id);

    @NotNull
    CompletableFuture<Integer> deleteByWorld(@NotNull String server, @NotNull String world);

    @NotNull
    CompletableFuture<Integer> deleteByServer(@NotNull String server);
}

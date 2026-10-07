package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.home.Home;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HomeStore {

    @NotNull
    CompletableFuture<Void> initialize();

    // 读取指定所有者的全部记录, 按名称键排序.
    @NotNull
    CompletableFuture<List<Home>> loadByOwner(@NotNull UUID owner);

    // 按 UUID 查找记录, 不存在时返回空结果.
    @NotNull
    CompletableFuture<Optional<Home>> find(@NotNull UUID id);

    // 在指定所有者名下忽略大小写查找名称.
    @NotNull
    CompletableFuture<Optional<Home>> findByName(@NotNull UUID owner, @NotNull String nameIgnoreCase);

    // 查询指定所有者的实际记录数量.
    @NotNull
    CompletableFuture<Long> countByOwner(@NotNull UUID owner);

    // 新增记录, 调用方提供 UUID v7 与创建、修改时间.
    @NotNull
    CompletableFuture<SaveResult> create(@NotNull Home home);

    // 按 UUID 和所有者更新可编辑字段, 保留创建时间; 不存在时返回 NOT_FOUND.
    @NotNull
    CompletableFuture<SaveResult> update(@NotNull Home home);

    // 删除指定所有者的记录, 不存在时返回 false.
    @NotNull
    CompletableFuture<Boolean> delete(@NotNull UUID owner, @NotNull UUID id);

    // 按所有已提供的条件取交集删除, 返回实际删除数量.
    @NotNull
    CompletableFuture<Long> deleteAll(@NotNull Filter filter);

    record Filter(@Nullable UUID owner, @Nullable String server, @Nullable String world) {
        public Filter {
            if (owner == null && server == null && world == null) {
                throw new IllegalArgumentException("At least one home filter is required");
            }
        }
    }

    enum Status {
        SUCCESS, DUPLICATE_NAME, NOT_FOUND
    }

    // home 仅在成功时存在, 包含实际保存的创建时间.
    record SaveResult(@NotNull Status status, @Nullable Home home) {
    }
}
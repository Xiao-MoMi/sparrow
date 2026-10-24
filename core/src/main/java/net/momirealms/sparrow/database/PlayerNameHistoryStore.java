package net.momirealms.sparrow.database;

import net.momirealms.sparrow.database.model.PlayerNameRecord;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerNameHistoryStore {

    // 调用方持有对应 UUID 的玩家会话锁, observedAt 为本次观察时间的毫秒时间戳.
    @NotNull
    CompletableFuture<Void> recordIfChanged(@NotNull UUID uuid, @NotNull String name, long observedAt);

    @NotNull
    CompletableFuture<Optional<PlayerNameRecord>> findLatestByName(@NotNull String name);

    @NotNull
    CompletableFuture<Optional<PlayerNameRecord>> findLatestByUuid(@NotNull UUID uuid);

    // 按记录编号从旧到新返回全部变化, 包括改回旧名以及仅大小写发生变化的记录.
    @NotNull
    CompletableFuture<List<PlayerNameRecord>> history(@NotNull UUID uuid);
}
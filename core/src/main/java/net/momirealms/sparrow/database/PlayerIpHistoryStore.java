package net.momirealms.sparrow.database;

import net.momirealms.sparrow.database.model.PlayerIpRecord;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerIpHistoryStore {

    // 调用方持有对应 UUID 的玩家会话锁, observedAt 为本次观察时间的毫秒时间戳.
    @NotNull
    CompletableFuture<Void> recordIfChanged(@NotNull UUID uuid, @NotNull InetAddress ip, long observedAt);

    @NotNull
    CompletableFuture<Optional<PlayerIpRecord>> findLatestByUuid(@NotNull UUID uuid);

    // 统计该 UUID 使用过的不同地址数量.
    @NotNull
    CompletableFuture<Long> countAddresses(@NotNull UUID uuid);

    // 每个地址返回最近一条记录, 按记录编号从新到旧排列; offset 非负, limit 为正数.
    @NotNull
    CompletableFuture<List<PlayerIpRecord>> listAddresses(@NotNull UUID uuid, int offset, int limit);

    // 统计使用过该地址的不同 UUID 数量.
    @NotNull
    CompletableFuture<Long> countPlayers(@NotNull InetAddress ip);

    // 每个 UUID 返回关于该地址的最近一条记录, 按记录编号从新到旧排列; offset 非负, limit 为正数.
    @NotNull
    CompletableFuture<List<PlayerIpRecord>> listPlayers(@NotNull InetAddress ip, int offset, int limit);
}
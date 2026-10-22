package net.momirealms.sparrow.player;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.LazyReference;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// 玩家锁是一份租约: 持有服每 5 秒续期, 宕机后 15 秒内自然过期. 值只记录持有服, 便于排查
final class PlayerLock {
    private static final long TTL_MILLIS = 15_000;
    private static final String RENEW_SCRIPT = """
            for i = 1, #KEYS do
                redis.call('PEXPIRE', KEYS[i], ARGV[1])
            end
            return 1
            """;
    private static final byte[] TTL_ARGUMENT = Long.toString(TTL_MILLIS).getBytes(StandardCharsets.US_ASCII);

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final byte[] serverId = ServerConfig.serverId().getBytes(StandardCharsets.UTF_8);
    private final Executor retry = CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS, this.plugin.scheduler().async());
    private final LazyReference<RedisAsyncCommands<byte[], byte[]>> commands = LazyReference.oneTime(() -> this.plugin.redisConnector().connection().async());

    // 锁被占用时每 50 毫秒重试一次, 到达截止时间以 TimeoutException 失败
    @NotNull
    CompletableFuture<Void> acquire(@NotNull UUID uuid, long deadlineNanos) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        this.acquire(key(uuid), deadlineNanos, result);
        return result;
    }

    private void acquire(byte[] key, long deadlineNanos, CompletableFuture<Void> result) {
        this.commands.get().set(key, this.serverId, SetArgs.Builder.nx().px(TTL_MILLIS)).whenComplete((reply, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else if (reply != null) {
                result.complete(null);
            } else if (System.nanoTime() >= deadlineNanos) {
                result.completeExceptionally(new TimeoutException("Player lock is still held"));
            } else {
                this.retry.execute(() -> this.acquire(key, deadlineNanos, result));
            }
        });
    }

    // 传入的集合可能正被并发修改, 边遍历边收集
    @NotNull
    CompletableFuture<Void> renew(@NotNull Iterable<UUID> uuids) {
        List<byte[]> keys = new ArrayList<>();
        for (UUID uuid : uuids) {
            keys.add(key(uuid));
        }
        return this.commands.get()
                .<Long>eval(RENEW_SCRIPT, ScriptOutputType.INTEGER, keys.toArray(byte[][]::new), TTL_ARGUMENT)
                .toCompletableFuture()
                .thenApply(ignored -> null);
    }

    @NotNull
    CompletableFuture<Void> release(@NotNull UUID uuid) {
        return this.commands.get()
                .del(key(uuid))
                .toCompletableFuture()
                .thenApply(ignored -> null);
    }

    private static byte[] key(UUID uuid) {
        return ("sparrow:player:lock:" + uuid).getBytes(StandardCharsets.UTF_8);
    }
}
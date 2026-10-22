package net.momirealms.sparrow.data.internal;

import net.momirealms.sparrow.data.access.DataReader;
import net.momirealms.sparrow.data.access.DataHold;
import net.momirealms.sparrow.data.access.EntryView;
import net.momirealms.sparrow.data.definition.DataDefinition;
import net.momirealms.sparrow.data.message.DataChangedMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class RegisteredData<K, E, V> {
    final RedisDataBackend<K, E, V> backend;
    final LocalDataCache<K, E, V> cache;
    final Executor executor;
    private final DataDefinition<K, E, V> definition;
    private final Map<CompletableFuture<?>, K> active = new HashMap<>();
    private @Nullable ScheduledFuture<?> renewalTask;
    private CompletableFuture<Void> renewing = CompletableFuture.completedFuture(null);
    private boolean renewalStopped;
    private CompletableFuture<Void> closing;

    RegisteredData(@NotNull DataDefinition<K, E, V> definition) {
        this.definition = definition;
        this.backend = new RedisDataBackend<>(definition);
        this.cache = new LocalDataCache<>(definition, this.backend);
        this.executor = SparrowPlugin.instance().scheduler().async();
    }

    @ApiStatus.Internal
    public void startRenewal(@NotNull ScheduledExecutorService scheduler) {
        if (this.definition.redisTtl() == null) return;
        long interval = Math.max(1, this.definition.redisTtl().toMillis() / 3);
        this.renewalTask = scheduler.scheduleAtFixedRate(this::renew, interval, interval, TimeUnit.MILLISECONDS);
    }

    @NotNull
    public DataReader<E, V> reader(@NotNull K key) {
        return new DataReader<>() {
            @Nullable
            @Override
            public EntryView<E, V> peek() {
                return RegisteredData.this.cache.peek(key);
            }

            @NotNull
            @Override
            public CompletableFuture<EntryView<E, V>> read() {
                return RegisteredData.this.execute(key, () -> RegisteredData.this.cache.read(key).thenApply(entries -> entries));
            }

            @NotNull
            @Override
            public CompletableFuture<DataHold<E, V>> hold() {
                CompletableFuture<DataHold<E, V>> result = new CompletableFuture<>();
                RegisteredData.this.execute(key, () -> RegisteredData.this.cache.hold(key, result)).whenComplete((ignored, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    }
                });
                return result;
            }
        };
    }

    final <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> operation) {
        return this.execute(null, operation);
    }

    final <T> CompletableFuture<T> execute(@Nullable K key, Supplier<CompletableFuture<T>> operation) {
        // 同一份记录用于关闭等待和 Redis 保活, 取消对外 Future 不会移除底层操作
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (this) {
            if (this.closing != null) return CompletableFuture.failedFuture(new IllegalStateException("Data is closed"));
            this.active.put(result, key);
        }
        CompletableFuture.completedFuture(null)
                .thenCompose(ignored -> operation.get())
                .whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                    synchronized (this) {
                        this.active.remove(result);
                    }
                });
        return result.copy();
    }

    // 调用方持有本注册数据的锁, 收集时不访问 Redis
    void collectUsedKeys(@NotNull Set<K> keys) {
        for (K key : this.active.values()) {
            if (key != null) {
                keys.add(key);
            }
        }
        this.cache.collectUsedKeys(keys);
    }

    // 专用定时线程只取本地快照并发出异步命令, 同一类型上一轮未结束时不再叠加
    private void renew() {
        Set<K> keys;
        CompletableFuture<Void> selected;
        synchronized (this) {
            if (this.renewalStopped || !this.renewing.isDone()) return;
            keys = new HashSet<>();
            this.collectUsedKeys(keys);
            if (keys.isEmpty()) return;
            selected = new CompletableFuture<>();
            this.renewing = selected;
        }
        this.backend.renew(keys).whenComplete((ignored, error) -> {
            if (error == null) {
                selected.complete(null);
            } else {
                SparrowPlugin.instance().logger().warn("Failed to renew Redis data " + this.definition.id(), error);
                selected.completeExceptionally(error);
            }
        });
    }

    public void receive(@NotNull DataChangedMessage message) {
        this.cache.receive(message);
    }

    @NotNull
    public CompletableFuture<Void> closeAsync() {
        CompletableFuture<Void> result;
        CompletableFuture<Void> accepted;
        synchronized (this) {
            if (this.closing != null) return this.closing.copy();
            result = new CompletableFuture<>();
            this.closing = result;
            accepted = CompletableFuture.allOf(this.active.keySet().toArray(CompletableFuture[]::new));
        }
        // 已接受的操作仍需要保活, 等它们结束后才停止续期
        accepted.handle((ignored, error) -> null)
                .thenCompose(ignored -> this.stopRenewal())
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
        return result.copy();
    }

    private synchronized CompletableFuture<Void> stopRenewal() {
        this.renewalStopped = true;
        if (this.renewalTask != null) {
            this.renewalTask.cancel(false);
        }
        this.cache.clear();
        // 续期异常已在请求完成边界报告, 关闭只等待已发出的命令结束
        return this.renewing.handle((ignored, error) -> null);
    }
}
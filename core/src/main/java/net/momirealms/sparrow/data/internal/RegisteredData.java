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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class RegisteredData<K, E, V> {
    final RedisDataBackend<K, E, V> backend;
    final LocalDataCache<K, E, V> cache;
    final Executor executor;
    private final Set<CompletableFuture<?>> active = new HashSet<>();
    private CompletableFuture<Void> closing;

    RegisteredData(@NotNull DataDefinition<K, E, V> definition) {
        this.backend = new RedisDataBackend<>(definition);
        this.cache = new LocalDataCache<>(definition, this.backend);
        this.executor = SparrowPlugin.instance().scheduler().async();
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
                return RegisteredData.this.execute(() -> RegisteredData.this.cache.read(key).thenApply(entries -> entries));
            }

            @NotNull
            @Override
            public CompletableFuture<DataHold<E, V>> hold() {
                CompletableFuture<DataHold<E, V>> result = new CompletableFuture<>();
                RegisteredData.this.execute(() -> RegisteredData.this.cache.hold(key, result)).whenComplete((ignored, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    }
                });
                return result;
            }
        };
    }

    // 登记一次已接受的操作, closeAsync 会等它结束. operation 在调用线程上立即执行
    final <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (this) {
            if (this.closing != null) return CompletableFuture.failedFuture(new IllegalStateException("Data is closed"));
            this.active.add(result);
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

    public void receive(@NotNull DataChangedMessage message) {
        this.cache.receive(message);
    }

    @NotNull
    public synchronized CompletableFuture<Void> closeAsync() {
        if (this.closing != null) return this.closing.copy();
        // 操作的异常已交给各自的调用方, 关闭只等待它们结束
        this.closing = CompletableFuture.allOf(this.active.toArray(CompletableFuture[]::new)).handle((ignored, error) -> {
            this.cache.clear();
            return null;
        });
        return this.closing.copy();
    }
}
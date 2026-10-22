package net.momirealms.sparrow.data.internal;

import net.momirealms.sparrow.data.access.SingleWriterData;
import net.momirealms.sparrow.data.access.SingleWriterEntries;
import net.momirealms.sparrow.data.definition.SingleWriterDefinition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class SingleWriterDataImpl<K, E, V> extends RegisteredData<K, E, V> implements SingleWriterData<K, E, V> {
    private final Map<K, CompletableFuture<SingleWriterEntriesImpl<K, E, V>>> writers = new HashMap<>();

    public SingleWriterDataImpl(@NotNull SingleWriterDefinition<K, E, V> definition) {
        super(definition);
    }

    // 同键只加载一次, 加载失败时移除记录, 下次调用重新加载
    @NotNull
    @Override
    public synchronized CompletableFuture<SingleWriterEntries<E, V>> load(@NotNull K key) {
        return this.execute(key, () -> {
            CompletableFuture<SingleWriterEntriesImpl<K, E, V>> loading = this.writers.get(key);
            if (loading == null) {
                CompletableFuture<SingleWriterEntriesImpl<K, E, V>> created = this.backend.load(key).thenApply(entries -> new SingleWriterEntriesImpl<>(this, key, entries));
                this.writers.put(key, created);
                created.whenComplete((writer, error) -> {
                    if (error != null) {
                        synchronized (this) {
                            this.writers.remove(key, created);
                        }
                    }
                });
                loading = created;
            }
            return loading.thenApply(writer -> writer);
        });
    }

    @NotNull
    @Override
    public CompletableFuture<Void> release(@NotNull K key) {
        return this.execute(key, () -> {
            CompletableFuture<SingleWriterEntriesImpl<K, E, V>> loaded;
            synchronized (this) {
                loaded = this.writers.get(key);
            }
            if (loaded == null) return CompletableFuture.completedFuture(null);
            return loaded.thenCompose(SingleWriterEntriesImpl::release).thenRun(() -> {
                synchronized (this) {
                    this.writers.remove(key, loaded);
                }
            });
        });
    }

    @NotNull
    @Override
    public CompletableFuture<Void> saveDirty() {
        return this.execute(() -> {
            List<CompletableFuture<SingleWriterEntriesImpl<K, E, V>>> selected;
            synchronized (this) {
                selected = new ArrayList<>(this.writers.values());
            }
            CompletableFuture<?>[] saving = new CompletableFuture<?>[selected.size()];
            for (int i = 0; i < saving.length; i++) {
                saving[i] = selected.get(i).thenCompose(SingleWriterEntriesImpl::saveIfDirty);
            }
            return CompletableFuture.allOf(saving);
        });
    }

    @Override
    void collectUsedKeys(@NotNull Set<K> keys) {
        super.collectUsedKeys(keys);
        keys.addAll(this.writers.keySet());
    }
}
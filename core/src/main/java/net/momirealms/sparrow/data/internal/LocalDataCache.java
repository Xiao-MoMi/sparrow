package net.momirealms.sparrow.data.internal;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable.TableEntry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.momirealms.sparrow.data.access.DataHold;
import net.momirealms.sparrow.data.definition.DataDefinition;
import net.momirealms.sparrow.data.message.DataChangedMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

// 本服的只读副本. 副本只由自己的加载结果, 按序到达的通知和本服提交回包更新
final class LocalDataCache<K, E, V> {
    private final DataDefinition<K, E, V> definition;
    private final RedisDataBackend<K, E, V> backend;
    private final Cache<K, LocalEntries<E, V>> cache;
    private final ConcurrentChainedObject2ObjectHashTable<K, Holding<E, V>> held = new ConcurrentChainedObject2ObjectHashTable<>();
    private final Map<K, Pending<E, V>> pending = new HashMap<>();
    private boolean closed;

    LocalDataCache(@NotNull DataDefinition<K, E, V> definition, @NotNull RedisDataBackend<K, E, V> backend) {
        this.definition = definition;
        this.backend = backend;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(definition.cachePolicy().idleTimeout())
                .maximumSize(definition.cachePolicy().maximumSize())
                .build();
    }

    @Nullable
    LocalEntries<E, V> peek(@NotNull K key) {
        Holding<E, V> holding = this.held.get(key);
        if (holding != null && holding.entries != null) return holding.entries;
        LocalEntries<E, V> cached = this.cache.getIfPresent(key);
        if (cached != null) return cached;
        // 第一次查持有表之后, 普通副本可能已被移入持有表
        holding = this.held.get(key);
        return holding == null ? null : holding.entries;
    }

    @NotNull
    CompletableFuture<LocalEntries<E, V>> read(@NotNull K key) {
        Pending<E, V> loading;
        synchronized (this) {
            LocalEntries<E, V> cached = this.peek(key);
            if (cached != null) return CompletableFuture.completedFuture(cached);
            loading = this.pending.get(key);
            if (loading != null) return loading.result.copy();
            loading = new Pending<>();
            this.pending.put(key, loading);
        }
        Pending<E, V> selected = loading;
        this.backend.load(key).whenComplete((entries, error) -> {
            synchronized (this) {
                this.pending.remove(key);
                if (error == null) {
                    // 加载期间暂存的通知按到达顺序回放, 快照已包含的版本跳过
                    for (int i = 0, size = selected.deltas.size(); i < size; i++) {
                        Delta<E, V> delta = selected.deltas.get(i);
                        if (delta.version == entries.version + 1) {
                            entries.apply(delta.changes, delta.version);
                        }
                    }
                    Holding<E, V> holding = this.held.get(key);
                    if (holding == null) {
                        this.cache.put(key, entries);
                    } else {
                        holding.entries = entries;
                    }
                }
            }
            if (error == null) {
                selected.result.complete(entries);
            } else {
                selected.result.completeExceptionally(error);
            }
        });
        // 每个等待者持有独立 Future, 取消等待不会取消共享读取
        return selected.result.copy();
    }

    // 对外申请可以取消, 返回的内部 Future 仍等共享加载结束, 供注销时等待
    @NotNull
    CompletableFuture<Void> hold(@NotNull K key, @NotNull CompletableFuture<DataHold<E, V>> result) {
        Hold handle;
        synchronized (this) {
            Holding<E, V> holding = this.held.get(key);
            if (holding == null) {
                holding = new Holding<>(this.cache.getIfPresent(key));
                this.held.put(key, holding);
                this.cache.invalidate(key);
            }
            holding.references++;
            handle = new Hold(key, holding);
        }
        result.whenComplete((ignored, error) -> {
            if (result.isCancelled()) {
                handle.close();
            }
        });
        return this.read(key).handle((entries, error) -> {
            if (error != null) {
                handle.close();
                result.completeExceptionally(error);
            } else if (!result.complete(handle)) {
                // 申请已取消或被调用方完成, 没有交付出去的句柄必须归还计数
                handle.close();
            }
            return null;
        });
    }

    // 通知只更新本服已有或正在加载的副本, 其他 key 直接忽略
    synchronized void receive(@NotNull DataChangedMessage message) {
        K key = this.definition.keyCodec().decode(message.key());
        LocalEntries<E, V> cached = this.peek(key);
        Pending<E, V> loading = cached == null ? this.pending.get(key) : null;
        if (cached == null && loading == null) return;
        byte[][] entries = message.entries();
        byte[][] values = message.values();
        Map<E, V> changes = HashMap.newHashMap(entries.length);
        for (int i = 0; i < entries.length; i++) {
            changes.put(this.definition.entryCodec().decode(entries[i]), values[i] == null ? null : this.definition.valueCodec().decode(values[i]));
        }
        if (cached == null) {
            loading.deltas.add(new Delta<>(message.version(), changes));
        } else if (message.version() == cached.version + 1) {
            cached.apply(changes, message.version());
        }
    }

    // 本服提交的回包与通知走不同连接. 只在版本正好接续时应用, 否则等通知按序补上
    synchronized void applied(@NotNull K key, long version, @NotNull Map<E, V> changes) {
        LocalEntries<E, V> cached = this.peek(key);
        if (cached != null && cached.version == version - 1) {
            cached.apply(changes, version);
        }
    }

    // 普通缓存的迭代会跳过已过期项, 不重置访问时间; 迁移与收集共用此锁
    synchronized void collectUsedKeys(@NotNull Set<K> keys) {
        for (TableEntry<K, Holding<E, V>> entry : this.held) {
            keys.add(entry.getKey());
        }
        keys.addAll(this.pending.keySet());
        for (Map.Entry<K, LocalEntries<E, V>> entry : this.cache.asMap().entrySet()) {
            keys.add(entry.getKey());
        }
    }

    synchronized void clear() {
        this.closed = true;
        this.held.clear();
        this.cache.invalidateAll();
    }

    private final class Hold implements DataHold<E, V> {
        private final K key;
        private final Holding<E, V> holding;
        private boolean closed;

        private Hold(K key, Holding<E, V> holding) {
            this.key = key;
            this.holding = holding;
        }

        @Nullable
        @Override
        public V get(@NotNull E entry) {
            return this.holding.entries.get(entry);
        }

        @NotNull
        @Override
        public V getOrDefault(@NotNull E entry, @NotNull V fallback) {
            return this.holding.entries.getOrDefault(entry, fallback);
        }

        @Override
        public int size() {
            return this.holding.entries.size();
        }

        @Override
        public void forEach(@NotNull BiConsumer<? super E, ? super V> action) {
            this.holding.entries.forEach(action);
        }

        @Override
        public void close() {
            synchronized (LocalDataCache.this) {
                if (this.closed) return;
                this.closed = true;
                if (--this.holding.references != 0 || LocalDataCache.this.closed) return;
                // 先放回普通缓存再移出持有表, 读取和通知沿用同一个副本
                if (this.holding.entries != null && LocalDataCache.this.definition.cachePolicy().maximumSize() > 0) {
                    LocalDataCache.this.cache.put(this.key, this.holding.entries);
                }
                LocalDataCache.this.held.remove(this.key, this.holding);
            }
        }
    }

    private static final class Holding<E, V> {
        private volatile LocalEntries<E, V> entries;
        private int references;

        private Holding(@Nullable LocalEntries<E, V> entries) {
            this.entries = entries;
        }
    }

    private record Delta<E, V>(long version, Map<E, V> changes) {
    }

    private static final class Pending<E, V> {
        private final CompletableFuture<LocalEntries<E, V>> result = new CompletableFuture<>();
        private final List<Delta<E, V>> deltas = new ArrayList<>();
    }
}
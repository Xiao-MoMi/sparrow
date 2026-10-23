package net.momirealms.sparrow.data.internal;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable.TableEntry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.momirealms.sparrow.data.definition.DataDefinition;
import net.momirealms.sparrow.data.message.DataChangedMessage;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// 本服的只读副本. 本服有会话的玩家, 副本放在存活表里一直保留, 其余放在普通缓存中按策略过期.
// 副本只由自己的加载结果, 按序到达的通知和本服提交回包更新
final class LocalDataCache<E, V> {
    private final PlayerManager players = SparrowPlugin.instance().playerManager();
    private final DataDefinition<E, V> definition;
    private final RedisDataBackend<E, V> backend;
    private final Cache<UUID, LocalEntries<E, V>> cache;
    private final ConcurrentChainedObject2ObjectHashTable<UUID, LocalEntries<E, V>> retained = new ConcurrentChainedObject2ObjectHashTable<>();
    private final Map<UUID, Pending<E, V>> pending = new HashMap<>();

    LocalDataCache(@NotNull DataDefinition<E, V> definition, @NotNull RedisDataBackend<E, V> backend) {
        this.definition = definition;
        this.backend = backend;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(definition.cachePolicy().idleTimeout())
                .maximumSize(definition.cachePolicy().maximumSize())
                .build();
    }

    @Nullable
    LocalEntries<E, V> peek(@NotNull UUID key) {
        LocalEntries<E, V> retained = this.retained.get(key);
        if (retained != null) return retained;
        LocalEntries<E, V> cached = this.cache.getIfPresent(key);
        if (cached != null) return cached;
        // 第一次查存活表之后, 普通副本可能已被移入存活表
        return this.retained.get(key);
    }

    @NotNull
    CompletableFuture<LocalEntries<E, V>> read(@NotNull UUID key) {
        Pending<E, V> loading;
        synchronized (this) {
            LocalEntries<E, V> cached = this.peek(key);
            if (cached != null) {
                // 玩家建立会话前读到的普通副本, 在会话期间被读到时移入存活表
                if (this.retained.get(key) == null && this.players.hasSession(key)) {
                    this.retained.put(key, cached);
                    this.cache.invalidate(key);
                }
                return CompletableFuture.completedFuture(cached);
            }
            loading = this.pending.get(key);
            if (loading != null) return loading.result;
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
                    if (this.players.hasSession(key)) {
                        this.retained.put(key, entries);
                    } else {
                        this.cache.put(key, entries);
                    }
                }
            }
            if (error == null) {
                selected.result.complete(entries);
            } else {
                selected.result.completeExceptionally(error);
            }
        });
        // 调用方通过后续异步阶段隔离取消, 共享加载结果供内部复用
        return selected.result;
    }

    // 每秒把会话已结束的玩家副本降回普通缓存, 之后按空闲时间过期
    synchronized void demoteInactive() {
        Iterator<TableEntry<UUID, LocalEntries<E, V>>> iterator = this.retained.entryIterator();
        while (iterator.hasNext()) {
            TableEntry<UUID, LocalEntries<E, V>> entry = iterator.next();
            if (this.players.hasSession(entry.getKey())) continue;
            // 先放回普通缓存再移出存活表, 读取和通知沿用同一个副本
            if (this.definition.cachePolicy().maximumSize() > 0) {
                this.cache.put(entry.getKey(), entry.getValue());
            }
            iterator.remove();
        }
    }

    // 通知只更新本服已有或正在加载的副本, 其他 key 直接忽略
    synchronized void receive(@NotNull DataChangedMessage message) {
        UUID key = UUIDUtils.fromBytes(message.key());
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
    synchronized void applied(@NotNull UUID key, long version, @NotNull Map<E, V> changes) {
        LocalEntries<E, V> cached = this.peek(key);
        if (cached != null && cached.version == version - 1) {
            cached.apply(changes, version);
        }
    }

    // 普通缓存的迭代会跳过已过期项, 不重置访问时间; 迁移与收集共用此锁
    synchronized void collectUsedKeys(@NotNull Set<UUID> keys) {
        for (TableEntry<UUID, LocalEntries<E, V>> entry : this.retained) {
            keys.add(entry.getKey());
        }
        keys.addAll(this.pending.keySet());
        for (Map.Entry<UUID, LocalEntries<E, V>> entry : this.cache.asMap().entrySet()) {
            keys.add(entry.getKey());
        }
    }

    synchronized void clear() {
        this.retained.clear();
        this.cache.invalidateAll();
    }

    private record Delta<E, V>(long version, Map<E, V> changes) {
    }

    private static final class Pending<E, V> {
        private final CompletableFuture<LocalEntries<E, V>> result = new CompletableFuture<>();
        private final List<Delta<E, V>> deltas = new ArrayList<>();
    }
}
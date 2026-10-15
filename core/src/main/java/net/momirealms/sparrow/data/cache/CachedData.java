package net.momirealms.sparrow.data.cache;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@ApiStatus.Internal
public final class CachedData<K, V> implements AutoCloseable {
    private static final Duration OFFLINE_IDLE = Duration.ofMinutes(5);

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final CacheDefinition<K, V> definition;
    private final Function<? super K, @Nullable UUID> playerIdMapper;
    private final RedisDataCache<K, V> redisCache;
    private final AsyncCache<Entry, V> loads = Caffeine.newBuilder().buildAsync(); // 条目身份隔离淘汰前后的加载
    private final Map<K, Entry> online = new HashMap<>(); // 本服在线条目, 由进退服回调迁移
    private final Cache<K, Entry> offline;               // 离线及无玩家归属的条目, 按最后访问时间淘汰
    private final ConcurrentChainedObject2ObjectHashTable<UUID, List<Entry>> playerEntries = new ConcurrentChainedObject2ObjectHashTable<>();
    private boolean closed;

    public CachedData(@NotNull CacheDefinition<K, V> definition) {
        this(definition, key -> null);
    }

    public CachedData(@NotNull CacheDefinition<K, V> definition, @NotNull Function<? super K, @Nullable UUID> playerIdMapper) {
        this.definition = definition;
        this.playerIdMapper = playerIdMapper;
        this.redisCache = new RedisDataCache<>(definition);
        this.offline = Caffeine.newBuilder()
                .expireAfterAccess(OFFLINE_IDLE)
                .scheduler(Scheduler.systemScheduler())
                .<K, Entry>evictionListener((key, entry, cause) -> {
                    this.loads.synchronous().invalidate(entry);
                    this.unindexEntry(entry);
                })
                .build();
    }

    // 将尚未过期的玩家条目转为在线保留, 沿用已有快照与加载.
    public synchronized void onJoin(@NotNull UUID playerId) {
        if (this.closed) return;
        List<Entry> entries = this.playerEntries.get(playerId);
        if (entries == null) return;
        for (int i = 0, size = entries.size(); i < size; i++) {
            Entry entry = entries.get(i);
            if (this.offline.asMap().remove(entry.key, entry)) {
                this.online.put(entry.key, entry);
            }
        }
    }

    // 将玩家条目移入离线缓存, 从离服时起计算五分钟闲置时间.
    public synchronized void onQuit(@NotNull UUID playerId) {
        if (this.closed) return;
        List<Entry> entries = this.playerEntries.get(playerId);
        if (entries == null) return;
        for (int i = 0, size = entries.size(); i < size; i++) {
            Entry entry = entries.get(i);
            if (this.online.remove(entry.key, entry)) {
                this.offline.put(entry.key, entry);
            }
        }
    }

    // 读取有效结果并合并同一条目的并发加载, 返回的 Future 可独立取消.
    @NotNull
    public CompletableFuture<V> read(@NotNull K key) {
        Read read;
        synchronized (this) {
            if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Data cache is closed"));
            Entry entry = this.findEntry(key, true);
            if (entry == null) {
                entry = new Entry(key, this.playerIdMapper.apply(key));
                this.indexEntry(entry);
                if (entry.playerId != null && this.plugin.dataAccess().isOnline(entry.playerId)) {
                    this.online.put(key, entry);
                } else {
                    this.offline.put(key, entry);
                }
            }
            read = this.prepareRead(entry);
        }
        this.startLoad(read);
        return read.future.copy();
    }

    // 立即读取最近成功的快照, 缺失时返回 null. 离线命中计为一次访问, 在线刷新期间可取得旧值.
    @Nullable
    public synchronized V readCacheOrNull(@NotNull K key) {
        if (this.closed) throw new CancellationException("Data cache is closed");
        Entry entry = this.findEntry(key, true);
        return entry == null ? null : entry.snapshot;
    }

    // 供允许阻塞的调用点等待有效结果; 刷新期间等待当前查询完成.
    @NotNull
    public V readCacheJoin(@NotNull K key) {
        return this.read(key).join();
    }

    // 按玩家发布不可变条目列表, 进退服只需遍历目标玩家的数据.
    private void indexEntry(Entry entry) {
        if (entry.playerId == null) return;
        this.playerEntries.compute(entry.playerId, (playerId, entries) -> {
            if (entries == null) return List.of(entry);
            List<Entry> updated = new ArrayList<>(entries.size() + 1);
            updated.addAll(entries);
            updated.add(entry);
            return Collections.unmodifiableList(updated);
        });
    }

    // 淘汰回调可并发执行. 索引更新只操作列表, 按条目身份移除并释放空的玩家分组.
    private void unindexEntry(Entry entry) {
        if (entry.playerId == null) return;
        this.playerEntries.computeIfPresent(entry.playerId, (playerId, entries) -> {
            int index = entries.indexOf(entry);
            if (index < 0) return entries;
            if (entries.size() == 1) return null;
            List<Entry> updated = new ArrayList<>(entries);
            updated.remove(index);
            return List.copyOf(updated);
        });
    }

    // 在缓存锁内查找条目, recordAccess 控制是否更新离线访问时间.
    @Nullable
    private Entry findEntry(K key, boolean recordAccess) {
        Entry entry = this.online.get(key);
        if (entry != null) return entry;
        return recordAccess ? this.offline.getIfPresent(key) : this.offline.policy().getIfPresentQuietly(key);
    }

    // 在缓存锁内复用或登记加载, 数据源由 startLoad 在锁外调用.
    private Read prepareRead(Entry entry) {
        CompletableFuture<V> future = this.loads.getIfPresent(entry);
        boolean load = false;
        if (future == null) {
            CompletableFuture<V> candidate = new CompletableFuture<>();
            future = this.loads.get(entry, (ignored, executor) -> candidate);
            load = future == candidate;
        }
        return new Read(entry, future, load);
    }

    private void startLoad(Read read) {
        if (!read.load) return;
        CompletableFuture.completedFuture(read.entry.key)
                .thenCompose(this.redisCache::load)
                .whenComplete((value, error) -> this.completeLoad(read, value, error));
    }

    // 发布仍有效的加载结果, 再完成对应请求. 数据源异常随请求传播.
    private void completeLoad(Read read, @Nullable V value, @Nullable Throwable error) {
        synchronized (this) {
            Entry entry = read.entry;
            // 静默核对条目和有效 Future, 保留原有离线访问时间.
            if (!this.closed && this.findEntry(entry.key, false) == entry && this.loads.asMap().get(entry) == read.future) {
                if (error == null) {
                    entry.snapshot = value;
                } else {
                    this.loads.asMap().remove(entry, read.future);
                }
            } else {
                this.loads.asMap().remove(entry, read.future);
            }
        }
        // 完成 Future 会执行调用方回调, 放在缓存锁之外.
        if (error == null) {
            read.future.complete(value);
        } else {
            read.future.completeExceptionally(error);
        }
    }

    @NotNull
    public CompletableFuture<Void> invalidate(@NotNull K key) {
        return this.invalidateKeys(List.of(key));
    }

    // 数据源提交后使指定条目失效, 返回任务等待共享失效和本服在线刷新完成.
    @NotNull
    public CompletableFuture<Void> invalidateKeys(@NotNull List<K> keys) {
        return this.invalidateEntries(List.copyOf(new LinkedHashSet<>(keys)));
    }

    // 整个缓存域失效, 包括尚在加载的条目.
    @NotNull
    public CompletableFuture<Void> invalidateAll() {
        return this.invalidateEntries(null);
    }

    // null 表示整域失效, 空列表表示本次没有条目需要失效.
    private CompletableFuture<Void> invalidateEntries(@Nullable List<K> keys) {
        synchronized (this) {
            if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Data cache is closed"));
        }
        if (keys != null && keys.isEmpty()) return CompletableFuture.completedFuture(null);
        return this.redisCache.invalidate(keys).thenCompose(ignored -> this.invalidateLocal(keys));
    }

    // 应用远端通知, keys 为 null 时作用于整个缓存域.
    @NotNull
    public CompletableFuture<Void> acceptInvalidation(@NotNull UUID origin, @Nullable List<String> keys) {
        synchronized (this) {
            if (this.closed || this.redisCache.origin().equals(origin)) return CompletableFuture.completedFuture(null);
        }
        List<K> decoded = keys == null ? null : keys.stream().map(this.redisCache::decodeKey).toList();
        return this.invalidateLocal(decoded);
    }

    // 清理离线条目并刷新在线条目, 在线旧快照保留至刷新成功.
    private CompletableFuture<Void> invalidateLocal(@Nullable List<K> keys) {
        List<Read> refreshes = new ArrayList<>();
        synchronized (this) {
            if (this.closed) return CompletableFuture.completedFuture(null);
            if (keys == null) {
                this.loads.synchronous().invalidateAll();
                this.offline.invalidateAll();
                this.playerEntries.clear();
                for (Entry entry : this.online.values()) {
                    this.indexEntry(entry);
                    refreshes.add(this.prepareRead(entry));
                }
            } else {
                for (K key : keys) {
                    Entry entry = this.findEntry(key, false);
                    if (entry == null) continue;
                    this.loads.synchronous().invalidate(entry);
                    if (this.online.get(key) == entry) {
                        refreshes.add(this.prepareRead(entry));
                    } else {
                        this.offline.asMap().remove(key, entry);
                        this.unindexEntry(entry);
                    }
                }
            }
        }
        // 在线刷新在锁外启动, 汇总任务向调用方传播加载失败.
        CompletableFuture<?>[] futures = new CompletableFuture<?>[refreshes.size()];
        for (int i = 0, size = refreshes.size(); i < size; i++) {
            Read read = refreshes.get(i);
            this.startLoad(read);
            futures[i] = read.future;
        }
        return CompletableFuture.allOf(futures);
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.redisCache.close();
        this.loads.synchronous().invalidateAll();
        this.online.clear();
        this.offline.invalidateAll();
        this.playerEntries.clear();
    }

    @NotNull
    public SparrowKey id() {
        return this.definition.id();
    }

    @NotNull
    public String getStoragePrefix() {
        return this.redisCache.storagePrefix();
    }

    private final class Entry {
        private final K key;
        private final @Nullable UUID playerId;
        private @Nullable V snapshot; // 最近成功结果, 供刷新期间同步展示

        private Entry(K key, @Nullable UUID playerId) {
            this.key = key;
            this.playerId = playerId;
        }
    }

    private final class Read {
        private final Entry entry;
        private final CompletableFuture<V> future;
        private final boolean load;

        private Read(Entry entry, CompletableFuture<V> future, boolean load) {
            this.entry = entry;
            this.future = future;
            this.load = load;
        }
    }
}
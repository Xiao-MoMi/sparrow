package net.momirealms.sparrow.data;

import net.momirealms.sparrow.data.cache.CacheDefinition;
import net.momirealms.sparrow.data.cache.CachedData;
import net.momirealms.sparrow.data.message.DataCacheInvalidationMessage;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@ApiStatus.Internal
public final class DataAccess implements PlayerListener, AutoCloseable {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Map<SparrowKey, CachedData<?, ?>> caches = new HashMap<>();
    private boolean listening;
    private boolean closed;

    // 接入玩家生命周期
    public synchronized void onEnable() {
        this.plugin.playerManager().registerListener(this);
        this.listening = true;
    }

    // 注册普通缓存, 条目按离线闲置规则淘汰.
    @NotNull
    public <K, V> CachedData<K, V> open(@NotNull CacheDefinition<K, V> definition) {
        return this.open(definition, key -> null);
    }

    // 每个数据 id 在本服注册一个缓存. 映射结果为 null 的条目按普通离线缓存管理.
    @NotNull
    public synchronized <K, V> CachedData<K, V> open(@NotNull CacheDefinition<K, V> definition, @NotNull Function<? super K, @Nullable UUID> playerIdMapper) {
        if (this.closed) throw new CancellationException("Data access is closed");
        CachedData<K, V> cache = new CachedData<>(definition, playerIdMapper);
        if (this.caches.putIfAbsent(cache.id(), cache) != null) {
            cache.close();
            throw new IllegalArgumentException("Data cache id is already open: " + cache.id());
        }
        return cache;
    }

    // 将当前有效会话的缓存条目转为在线保留.
    @Override
    public synchronized void onJoin(@NotNull BukkitSparrowPlayer player) {
        if (this.closed || player.quitting() || this.plugin.playerManager().getPlayer(player.uniqueId()) != player) return;
        for (CachedData<?, ?> cache : this.caches.values()) {
            cache.onJoin(player.uniqueId());
        }
    }

    // 仅当前会话退出时开始离线计时, 旧连接的退出回调保持新会话有效.
    @Override
    public synchronized void onQuit(@NotNull BukkitSparrowPlayer player) {
        if (this.closed || this.plugin.playerManager().getPlayer(player.uniqueId()) != player) return;
        for (CachedData<?, ?> cache : this.caches.values()) {
            cache.onQuit(player.uniqueId());
        }
    }

    public boolean isOnline(@NotNull UUID playerId) {
        BukkitSparrowPlayer player = this.plugin.playerManager().getPlayer(playerId);
        return player != null && !player.quitting();
    }

    // 按数据 id 路由通知, 存储范围同时匹配时才应用到本服缓存.
    @NotNull
    public CompletableFuture<Void> acceptInvalidation(@NotNull DataCacheInvalidationMessage message) {
        CachedData<?, ?> cache;
        synchronized (this) {
            if (this.closed) return CompletableFuture.completedFuture(null);
            cache = this.caches.get(message.id());
        }
        if (cache == null || !message.storagePrefix().equals(cache.getStoragePrefix())) return CompletableFuture.completedFuture(null);
        return cache.acceptInvalidation(message.origin(), message.keys());
    }

    // 关闭指定实例并移除它的路由, 同 id 的新实例仍可继续使用.
    public synchronized void close(@NotNull CachedData<?, ?> cache) {
        this.caches.remove(cache.id(), cache);
        cache.close();
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        if (this.listening) {
            this.plugin.playerManager().unregisterListener(this);
            this.listening = false;
        }
        for (CachedData<?, ?> cache : this.caches.values()) {
            cache.close();
        }
        this.caches.clear();
    }
}
package net.momirealms.sparrow.feature.home;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

final class HomeCache implements AutoCloseable {
    private final HomeStore store;
    private final PluginLogger logger;
    private final ConcurrentChainedObject2ObjectHashTable<UUID, OwnerState> online = new ConcurrentChainedObject2ObjectHashTable<>();
    // 未进入游戏的登录尝试短暂保留, Join 后转入在线会话.
    private final Cache<UUID, OwnerState> pending = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).build();
    private boolean closed;

    HomeCache() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        this.store = plugin.dataStorage().homeStore();
        this.logger = plugin.logger();
    }

    @NotNull
    CompletableFuture<HomeSnapshot> preload(@NotNull UUID owner) {
        OwnerState state;
        OwnerState previous;
        synchronized (this) {
            if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home cache is closed"));
            state = new OwnerState(owner);
            previous = this.pending.asMap().put(owner, state);
        }
        if (previous != null) {
            previous.close();
        }
        return state.read();
    }

    void join(@NotNull BukkitSparrowPlayer player) {
        OwnerState state;
        OwnerState previous;
        synchronized (this) {
            if (this.closed) return;
            previous = this.online.get(player.uniqueId());
            if (previous != null && previous.player == player) {
                return;
            }
            state = this.pending.asMap().remove(player.uniqueId());
            if (state == null) {
                state = new OwnerState(player.uniqueId());
            }
            state.player = player;
            this.online.put(player.uniqueId(), state);
        }
        if (previous != null) {
            previous.close();
        }
        state.read();
    }

    void quit(@NotNull BukkitSparrowPlayer player) {
        OwnerState state;
        synchronized (this) {
            state = this.online.get(player.uniqueId());
            if (state == null || state.player != player) {
                return;
            }
            this.online.remove(player.uniqueId());
        }
        state.close();
    }

    // 未注册在线会话时返回 null, 离线查询由业务服务处理.
    @Nullable
    CompletableFuture<HomeSnapshot> read(@NotNull UUID owner) {
        OwnerState state = this.online.get(owner);
        return state == null ? null : state.read();
    }

    @Nullable
    HomeSnapshot cachedSnapshot(@NotNull UUID owner) {
        OwnerState state = this.online.get(owner);
        return state == null ? null : state.cached();
    }

    synchronized void invalidate(@Nullable UUID owner) {
        if (owner == null) {
            for (OwnerState state : this.online.values()) {
                state.invalidate();
            }
            for (OwnerState state : this.pending.asMap().values()) {
                state.invalidate();
            }
            return;
        }
        OwnerState state = this.online.get(owner);
        if (state != null) {
            state.invalidate();
        }
        OwnerState preparing = this.pending.getIfPresent(owner);
        if (preparing != null) {
            preparing.invalidate();
        }
    }

    @Override
    public void close() {
        List<OwnerState> states;
        synchronized (this) {
            this.closed = true;
            states = new ArrayList<>(this.online.values());
            states.addAll(this.pending.asMap().values());
            this.online.clear();
            this.pending.invalidateAll();
        }
        for (OwnerState state : states) {
            state.close();
        }
    }

    private final class OwnerState {
        private final UUID owner;
        private @Nullable BukkitSparrowPlayer player;
        private @Nullable HomeSnapshot snapshot;
        private @Nullable CompletableFuture<HomeSnapshot> loading;
        private boolean dirty;
        private boolean closed;

        private OwnerState(UUID owner) {
            this.owner = owner;
        }

        private CompletableFuture<HomeSnapshot> read() {
            CompletableFuture<HomeSnapshot> future;
            synchronized (this) {
                if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home owner has left"));
                if (this.loading != null) return this.loading.copy();
                if (this.snapshot != null && !this.dirty) {
                    return CompletableFuture.completedFuture(this.snapshot);
                }
                future = new CompletableFuture<>();
                this.loading = future;
            }
            this.load(future);
            return future.copy();
        }

        private void load(CompletableFuture<HomeSnapshot> future) {
            synchronized (this) {
                if (this.closed) return;
                this.dirty = false;
            }
            // I/O 和 future 回调都在锁外运行, 避免阻塞进退服与消息处理.
            HomeCache.this.store.loadByOwner(this.owner).whenComplete((homes, error) -> {
                HomeSnapshot loaded = error == null ? new HomeSnapshot(homes) : null;
                boolean reread;
                synchronized (this) {
                    if (this.closed) return;
                    reread = error == null && this.dirty;
                    if (!reread) {
                        this.loading = null;
                        if (error == null) {
                            this.snapshot = loaded;
                        } else {
                            this.dirty = true;
                        }
                    }
                }
                if (reread) {
                    // 读取期间收到变更通知, 当前结果作废后重读.
                    this.load(future);
                } else if (error != null) {
                    future.completeExceptionally(error);
                    HomeCache.this.logger.warn("Failed to load homes for owner " + this.owner, error);
                } else {
                    future.complete(loaded);
                }
            });
        }

        @Nullable
        private HomeSnapshot cached() {
            HomeSnapshot cached;
            boolean refresh;
            synchronized (this) {
                cached = this.snapshot;
                refresh = !this.closed && this.loading == null && (cached == null || this.dirty);
            }
            if (refresh) {
                this.read();
            }
            return cached;
        }

        private synchronized void invalidate() {
            if (this.closed) return;
            this.dirty = true;
        }

        private void close() {
            CompletableFuture<HomeSnapshot> pending;
            synchronized (this) {
                this.closed = true;
                this.snapshot = null;
                pending = this.loading;
                this.loading = null;
            }
            if (pending != null) {
                pending.cancel(false);
            }
        }
    }
}
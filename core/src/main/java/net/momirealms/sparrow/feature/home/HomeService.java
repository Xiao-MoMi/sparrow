package net.momirealms.sparrow.feature.home;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class HomeService implements AutoCloseable {
    private final HomeStore store;
    private final String serverId;
    private final PluginLogger logger;
    private final long ttlNanos;
    private final LongSupplier clock = System::nanoTime;
    private final ConcurrentChainedObject2ObjectHashTable<UUID, OwnerState> online = new ConcurrentChainedObject2ObjectHashTable<>();
    private volatile boolean closed;

    public HomeService() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        this.store = plugin.dataStorage().homeStore();
        this.serverId = ServerConfig.serverId();
        this.logger = plugin.logger();
        this.ttlNanos = TimeUnit.SECONDS.toNanos(plugin.configurationManager().featuresConfig().config().home().cacheTtlSeconds());
    }

    public void join(@NotNull SparrowPlayer player) {
        OwnerState state;
        OwnerState previous;
        synchronized (this) {
            if (this.closed) return;
            previous = this.online.get(player.uniqueId());
            if (previous != null && previous.player == player) return;
            state = new OwnerState(player);
            this.online.put(player.uniqueId(), state);
        }
        if (previous != null) {
            previous.close();
        }
        state.read();
    }

    public void quit(@NotNull SparrowPlayer player) {
        OwnerState state;
        synchronized (this) {
            state = this.online.get(player.uniqueId());
            if (state == null || state.player != player) return;
            this.online.remove(player.uniqueId());
        }
        state.close();
    }

    // 在线玩家复用完整快照; 离线查询只返回本次结果, 不留下常驻缓存.
    @NotNull
    public CompletableFuture<HomeSnapshot> snapshot(@NotNull UUID owner) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        OwnerState state = this.online.get(owner);
        return state == null ? this.store.loadByOwner(owner).thenApply(HomeSnapshot::new) : state.read();
    }

    // 同步补全允许暂用过期建议, 但会在后台共用一次刷新.
    @NotNull
    public List<String> complete(@NotNull UUID owner, @NotNull String input, int limit) {
        OwnerState state = this.online.get(owner);
        if (state == null) return List.of();
        state.read();
        HomeSnapshot snapshot = state.cached();
        return snapshot == null ? List.of() : snapshot.complete(input, limit);
    }

    @NotNull
    public CompletableFuture<HomeStore.SaveResult> create(@NotNull Home home) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.create(home).thenApply(this::saved);
    }

    @NotNull
    public CompletableFuture<HomeStore.SaveResult> update(@NotNull Home home) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.update(home).thenApply(this::saved);
    }

    private HomeStore.SaveResult saved(HomeStore.SaveResult result) {
        if (result.status() == HomeStore.Status.SUCCESS) {
            this.committed(HomeChangedMessage.save(this.serverId, result.home()));
        }
        return result;
    }

    @NotNull
    public CompletableFuture<Optional<HomeStore.DeleteResult>> delete(@NotNull UUID owner, @NotNull UUID id) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.delete(owner, id).thenApply(deleted -> {
            deleted.ifPresent(result -> this.committed(HomeChangedMessage.delete(this.serverId, result)));
            return deleted;
        });
    }

    @NotNull
    public CompletableFuture<Long> deleteByOwner(@NotNull UUID owner) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.deleteByOwner(owner).thenApply(deleted -> {
            this.committed(HomeChangedMessage.invalidateOwner(this.serverId, owner));
            return deleted;
        });
    }

    private void committed(HomeChangedMessage message) {
        this.apply(message);
        // 数据库已提交, 发布失败不能让调用方误以为保存失败而重试创建.
        try {
            SparrowPlugin.instance().messageBrokerManager().publishOneWay(message, "").whenComplete((subscribers, error) -> {
                if (error != null) {
                    this.logger.warn("Home changes were saved, but could not be published for owner " + message.owner(), error);
                }
            });
        } catch (RuntimeException exception) {
            this.logger.warn("Home changes were saved, but could not be published for owner " + message.owner(), exception);
        }
    }

    public void accept(@NotNull HomeChangedMessage message) {
        if (this.serverId.equals(message.origin())) return;
        this.apply(message);
    }

    private void apply(HomeChangedMessage message) {
        OwnerState state = this.online.get(message.owner());
        if (state != null) {
            state.apply(message);
        }
    }

    @Override
    public void close() {
        List<OwnerState> states;
        synchronized (this) {
            this.closed = true;
            states = List.copyOf(this.online.values());
            this.online.clear();
        }
        for (OwnerState state : states) state.close();
    }

    private final class OwnerState {
        private final SparrowPlayer player;
        private @Nullable HomeSnapshot snapshot;
        private long loadedAt;
        private @Nullable CompletableFuture<HomeSnapshot> loading;
        private boolean dirty;
        private boolean closed;

        private OwnerState(SparrowPlayer player) {
            this.player = player;
        }

        private CompletableFuture<HomeSnapshot> read() {
            CompletableFuture<HomeSnapshot> future;
            synchronized (this) {
                if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home owner has left"));
                if (this.loading != null) return this.loading.copy();
                if (this.snapshot != null && HomeService.this.clock.getAsLong() - this.loadedAt < HomeService.this.ttlNanos) {
                    return CompletableFuture.completedFuture(this.snapshot);
                }
                future = new CompletableFuture<>();
                this.loading = future;
            }
            this.load(future);
            return future.copy();
        }

        private void load(CompletableFuture<HomeSnapshot> future) {
            long startedAt;
            synchronized (this) {
                if (this.closed) return;
                this.dirty = false;
                startedAt = HomeService.this.clock.getAsLong();
            }
            // I/O 和 future 回调都在锁外运行, 避免阻塞进退服与消息处理.
            HomeService.this.store.loadByOwner(this.player.uniqueId()).whenComplete((homes, error) -> {
                HomeSnapshot loaded = error == null ? new HomeSnapshot(homes) : null;
                boolean retry;
                synchronized (this) {
                    if (this.closed) return;
                    retry = error == null && this.dirty;
                    if (!retry) {
                        this.loading = null;
                        if (error == null) {
                            this.snapshot = loaded;
                            this.loadedAt = startedAt;
                        }
                    }
                }
                if (retry) {
                    this.load(future);
                } else if (error != null) {
                    future.completeExceptionally(error);
                    HomeService.this.logger.warn("Failed to load homes for owner " + this.player.uniqueId(), error);
                } else {
                    future.complete(loaded);
                }
            });
        }

        @Nullable
        private synchronized HomeSnapshot cached() {
            return this.snapshot;
        }

        private synchronized void apply(HomeChangedMessage message) {
            if (this.closed) return;
            if (this.loading != null) {
                this.dirty = true;
            }
            this.snapshot = switch (message.type()) {
                case SAVE -> this.snapshot == null ? null : this.snapshot.save(message.home());
                case DELETE -> this.snapshot == null ? null : this.snapshot.delete(message.deleted().id());
                case INVALIDATE_OWNER -> null;
            };
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

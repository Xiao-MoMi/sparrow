package net.momirealms.sparrow.feature.home;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public final class HomeService implements AutoCloseable {
    private final HomeStore store;
    private final String serverId;
    private final PluginLogger logger;
    private final ConcurrentChainedObject2ObjectHashTable<UUID, OwnerState> online = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, CompletableFuture<Void>> writes = new ConcurrentChainedObject2ObjectHashTable<>();
    private volatile boolean closed;

    public HomeService() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        this.store = plugin.dataStorage().homeStore();
        this.serverId = ServerConfig.serverId();
        this.logger = plugin.logger();
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

    // 读取在线缓存, 未加载或已失效时返回 null.
    @Nullable
    public HomeSnapshot cachedSnapshot(@NotNull UUID owner) {
        OwnerState state = this.online.get(owner);
        return state == null ? null : state.cached();
    }

    @NotNull
    public CompletableFuture<Optional<Home>> find(@NotNull UUID owner, @NotNull String name) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.findByName(owner, name);
    }

    // 同步补全共用正在加载的查询, 完成后下次补全即可取得结果.
    @NotNull
    public List<String> complete(@NotNull UUID owner, @NotNull String input, int limit) {
        OwnerState state = this.online.get(owner);
        if (state == null) return List.of();
        HomeSnapshot snapshot = state.read().getNow(null);
        return snapshot == null ? List.of() : snapshot.complete(input, limit);
    }

    // 同一所有者的新增串行执行, 数量以写入前的数据库记录为准.
    @NotNull
    public CompletableFuture<Result> set(@NotNull UUID owner, @NotNull String name, @NotNull String server, @NotNull WorldLocation location, int limit) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> previous;
        synchronized (this) {
            if (this.closed) {
                return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
            }
            previous = this.writes.put(owner, gate);
        }
        CompletableFuture<Result> operation = (previous == null ? CompletableFuture.<Void>completedFuture(null) : previous)
                .thenCompose(ignored -> this.setNow(owner, name, server, location, limit));
        operation.whenComplete((result, error) -> {
            this.writes.remove(owner, gate);
            gate.complete(null);
        });
        return operation.copy();
    }

    private CompletableFuture<Result> setNow(UUID owner, String name, String server, WorldLocation location, int limit) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        HomeSettings settings = SparrowPlugin.instance().configurationManager().featuresConfig().config().home();
        if (!settings.validName(name)) {
            return CompletableFuture.completedFuture(new Result(Status.INVALID_NAME, null));
        }
        return this.store.findByName(owner, name).thenCompose(found -> {
            if (found.isPresent()) {
                return CompletableFuture.completedFuture(new Result(Status.DUPLICATE_NAME, null));
            }
            return this.store.countByOwner(owner).thenCompose(count -> {
                if (limit != CompatibilityManager.UNLIMITED && count >= limit) return CompletableFuture.completedFuture(new Result(Status.LIMIT_REACHED, null));
                long now = System.currentTimeMillis();
                return this.create(new Home(UUIDUtils.createV7(), owner, name, server, location, now, now))
                        .thenApply(saved -> this.result(saved, Status.CREATED));
            });
        });
    }

    // 编辑按 UUID 读取当前记录, 所有者在整个操作中保持不变.
    @NotNull
    public CompletableFuture<Result> rename(@NotNull UUID owner, @NotNull UUID id, @NotNull String name) {
        HomeSettings settings = SparrowPlugin.instance().configurationManager().featuresConfig().config().home();
        if (!settings.validName(name)) return CompletableFuture.completedFuture(new Result(Status.INVALID_NAME, null));
        return this.edit(owner, id, home -> new Home(home.id(), home.owner(), name, home.server(), home.location(), home.createdAt(), System.currentTimeMillis()));
    }

    @NotNull
    public CompletableFuture<Result> relocate(@NotNull UUID owner, @NotNull UUID id, @NotNull String server, @NotNull WorldLocation location) {
        return this.edit(owner, id, home -> new Home(home.id(), home.owner(), home.name(), server, location, home.createdAt(), System.currentTimeMillis()));
    }

    private CompletableFuture<Result> edit(UUID owner, UUID id, UnaryOperator<Home> operation) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.find(id).thenCompose(found -> {
            if (found.isEmpty() || !found.get().owner().equals(owner)) {
                return CompletableFuture.completedFuture(new Result(Status.NOT_FOUND, null));
            }
            return this.update(operation.apply(found.get())).thenApply(saved -> this.result(saved, Status.UPDATED));
        });
    }

    private Result result(HomeStore.SaveResult saved, Status success) {
        return new Result(switch (saved.status()) {
            case SUCCESS -> success;
            case DUPLICATE_NAME -> Status.DUPLICATE_NAME;
            case NOT_FOUND -> Status.NOT_FOUND;
        }, saved.home());
    }

    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID owner, @NotNull String name) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.findByName(owner, name).thenCompose(found ->
                found.isEmpty()
                ? CompletableFuture.completedFuture(false)
                : this.delete(owner, found.get().id())
        );
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
            this.committed(HomeChangedMessage.invalidateOwner(this.serverId, result.home().owner()));
        }
        return result;
    }

    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID owner, @NotNull UUID id) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.delete(owner, id).thenApply(deleted -> {
            if (deleted) {
                this.committed(HomeChangedMessage.invalidateOwner(this.serverId, owner));
            }
            return deleted;
        });
    }

    @NotNull
    public CompletableFuture<Long> deleteAll(@NotNull HomeStore.Filter filter) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.store.deleteAll(filter).thenApply(deleted -> {
            this.committed(filter.owner() == null ? HomeChangedMessage.invalidateAll(this.serverId) : HomeChangedMessage.invalidateOwner(this.serverId, filter.owner()));
            return deleted;
        });
    }

    private void committed(HomeChangedMessage message) {
        this.invalidate(message.owner());
        SparrowPlugin.instance().messageBrokerManager().publishOneWay(message, "");
    }

    public void accept(@NotNull HomeChangedMessage message) {
        if (this.serverId.equals(message.origin())) return;
        this.invalidate(message.owner());
    }

    private void invalidate(@Nullable UUID owner) {
        if (owner == null) {
            for (OwnerState state : this.online.values()) state.invalidate();
            return;
        }
        OwnerState state = this.online.get(owner);
        if (state != null) {
            state.invalidate();
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

    public enum Status {
        CREATED, UPDATED, DUPLICATE_NAME, NOT_FOUND, INVALID_NAME, LIMIT_REACHED
    }

    public record Result(@NotNull Status status, @Nullable Home home) {
    }

    private final class OwnerState {
        private final SparrowPlayer player;
        private @Nullable HomeSnapshot snapshot;
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
                if (this.snapshot != null) {
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
            HomeService.this.store.loadByOwner(this.player.uniqueId()).whenComplete((homes, error) -> {
                HomeSnapshot loaded = error == null ? new HomeSnapshot(homes) : null;
                boolean reread;
                synchronized (this) {
                    if (this.closed) return;
                    reread = error == null && this.dirty;
                    if (!reread) {
                        this.loading = null;
                        if (error == null) {
                            this.snapshot = loaded;
                        }
                    }
                }
                if (reread) {
                    // 读取期间收到变更通知, 当前结果作废后重读.
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

        private synchronized void invalidate() {
            if (this.closed) return;
            if (this.loading != null) {
                this.dirty = true;
            }
            this.snapshot = null;
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

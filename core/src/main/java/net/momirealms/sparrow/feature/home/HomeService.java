package net.momirealms.sparrow.feature.home;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.data.HomeDataAccess;
import net.momirealms.sparrow.plugin.SparrowPlugin;
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
    private final HomeDataAccess data = new HomeDataAccess();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, CompletableFuture<Void>> writes = new ConcurrentChainedObject2ObjectHashTable<>();
    private volatile boolean closed;

    public HomeService() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        this.store = plugin.dataStorage().homeStore();
    }

    void preload(@NotNull UUID owner) {
        this.data.read(owner).join();
    }

    @NotNull
    public CompletableFuture<HomeSnapshot> snapshot(@NotNull UUID owner) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        }
        return this.data.read(owner);
    }

    // 同步展示复用最近成功的快照, 在线刷新完成前继续返回旧值.
    @Nullable
    public HomeSnapshot cachedSnapshot(@NotNull UUID owner) {
        return this.data.readCacheOrNull(owner);
    }

    @NotNull
    public CompletableFuture<Optional<Home>> find(@NotNull UUID owner, @NotNull String name) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        }
        return this.data.read(owner).thenApply(snapshot -> Optional.ofNullable(snapshot.get(name)));
    }

    // 刷新期间沿用旧快照, 下次补全即可取得新结果.
    @NotNull
    public List<String> complete(@NotNull UUID owner, @NotNull String input, int limit) {
        HomeSnapshot snapshot = this.data.readCacheOrNull(owner);
        return snapshot == null ? List.of() : snapshot.complete(input, limit);
    }

    // 同一所有者的新增串行执行, 数量以写入前的数据库记录为准.
    @NotNull
    public CompletableFuture<Result> set(
            @NotNull UUID owner,
            @NotNull String name,
            @NotNull String server,
            @NotNull WorldLocation location,
            int limit
    ) {
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
        if (this.closed) {
            return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        }
        HomeSettings settings = SparrowPlugin.instance().configurationManager().featuresConfig().config().home();
        if (!settings.validName(name)) {
            return CompletableFuture.completedFuture(new Result(Status.INVALID_NAME, null));
        }
        return this.store.findByName(owner, name).thenCompose(found -> {
            if (found.isPresent()) {
                return CompletableFuture.completedFuture(new Result(Status.DUPLICATE_NAME, null));
            }
            return this.store.countByOwner(owner).thenCompose(count -> {
                if (limit != CompatibilityManager.UNLIMITED && count >= limit) {
                    return CompletableFuture.completedFuture(new Result(Status.LIMIT_REACHED, null));
                }
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
        if (!settings.validName(name)) {
            return CompletableFuture.completedFuture(new Result(Status.INVALID_NAME, null));
        }
        return this.edit(
                owner,
                id,
                home -> new Home(home.id(), home.owner(), name, home.server(), home.location(), home.createdAt(), System.currentTimeMillis())
        );
    }

    @NotNull
    public CompletableFuture<Result> relocate(@NotNull UUID owner, @NotNull UUID id, @NotNull String server, @NotNull WorldLocation location) {
        return this.edit(
                owner,
                id,
                home -> new Home(home.id(), home.owner(), home.name(), server, location, home.createdAt(), System.currentTimeMillis())
        );
    }

    private CompletableFuture<Result> edit(UUID owner, UUID id, UnaryOperator<Home> operation) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        }
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
        if (this.closed) {
            return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        }
        return this.store.findByName(owner, name).thenCompose(
                found ->
                found.isEmpty()
                ? CompletableFuture.completedFuture(false)
                : this.delete(owner, found.get().id())
        );
    }

    @NotNull
    public CompletableFuture<HomeStore.SaveResult> create(@NotNull Home home) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        }
        return this.data.create(home);
    }

    @NotNull
    public CompletableFuture<HomeStore.SaveResult> update(@NotNull Home home) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        }
        return this.data.update(home);
    }

    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID owner, @NotNull UUID id) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        }
        return this.data.delete(owner, id);
    }

    @NotNull
    public CompletableFuture<Long> deleteAll(@NotNull HomeStore.Filter filter) {
        if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Home service is closed"));
        return this.data.deleteAll(filter);
    }

    @Override
    public void close() {
        synchronized (this) {
            this.closed = true;
        }
        this.data.close();
    }

    public enum Status {
        CREATED, UPDATED, DUPLICATE_NAME, NOT_FOUND, INVALID_NAME, LIMIT_REACHED
    }

    public record Result(@NotNull Status status, @Nullable Home home) {
    }
}
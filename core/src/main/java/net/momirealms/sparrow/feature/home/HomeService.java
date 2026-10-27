package net.momirealms.sparrow.feature.home;

import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.compatibility.CompatibilityManager.PermissionValueMode;
import net.momirealms.sparrow.data.access.EntryEdit;
import net.momirealms.sparrow.data.access.EntryView;
import net.momirealms.sparrow.data.access.MultiWriterEntries;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerConnection;
import net.momirealms.sparrow.player.listener.PlayerJoinListener;
import net.momirealms.sparrow.player.listener.PlayerPrepareListener;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.util.ThrowableUtils;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public final class HomeService implements PlayerPrepareListener, PlayerJoinListener {
    private static final String LIMIT_PERMISSION = DependencyVersions.PROJECT_ID + ".home.limit";
    private static final int OWNER_BATCH_SIZE = 100;

    private final HomeStore store = SparrowPlugin.instance().dataStorage().homeStore();
    private final HomeData data;
    private final Supplier<HomeConfig> config;
    private final Map<UUID, Set<CompletableFuture<?>>> active = new HashMap<>();
    private boolean accepting;

    HomeService(HomeData data, Supplier<HomeConfig> config) {
        this.data = data;
        this.config = config;
    }

    synchronized void start() {
        this.accepting = true;
    }

    synchronized CompletableFuture<Void> preload(UUID owner) {
        if (!this.accepting) return CompletableFuture.completedFuture(null);
        return this.execute(owner, () -> this.data.entries(owner).read().thenApply(ignored -> null));
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onPrepare(@NotNull PlayerConnection connection) {
        return this.preload(connection.uniqueId());
    }

    @Override
    public void onJoin(@NotNull BukkitSparrowPlayer player) {
        // 热启用时可能已有玩家完成准备但尚未加入, 加入时补齐预加载.
        this.preload(player.uniqueId()).whenComplete((ignored, error) -> {
            if (error != null) SparrowPlugin.instance().logger().warn("Failed to preload homes for " + player.uniqueId(), ThrowableUtils.unwrap(error));
        });
    }

    public int getLimit(@NotNull BukkitSparrowPlayer player) {
        return player.getPermissionValue(LIMIT_PERMISSION, this.config.get().maxHomes(), PermissionValueMode.MAX);
    }

    @NotNull
    public CompletableFuture<List<Home>> list(@NotNull UUID owner) {
        return this.execute(owner, () -> this.data.entries(owner).read().thenApply(entries -> {
            List<Home> homes = new ArrayList<>();
            entries.forEach((id, home) -> homes.add(home));
            homes.sort(Comparator.comparing(Home::getNameKey));
            return List.copyOf(homes);
        }));
    }

    @NotNull
    public CompletableFuture<Optional<Home>> find(@NotNull UUID owner, @NotNull String name) {
        String key = Home.key(name);
        return this.execute(owner, () -> this.data.entries(owner).read().thenApply(entries -> Optional.ofNullable(this.findByName(entries, key))));
    }

    @NotNull
    public CompletableFuture<Result> create(@NotNull UUID owner, @NotNull String name, @NotNull String server, @NotNull WorldLocation location, int limit) {
        HomeConfig config = this.config.get();
        if (!config.validName(name)) return CompletableFuture.completedFuture(new Result(Status.INVALID_NAME, null));
        String key = Home.key(name);
        long now = System.currentTimeMillis();
        Home home = new Home(UUIDUtils.createV7(), owner, name, server, location, now, now);
        // 上限由调用方按所有者权限取得, 与 UUID 和时间一起固定为本次操作的输入.
        return this.modify(owner, entries -> {
            if (this.findByName(entries, key) != null) return new Result(Status.DUPLICATE_NAME, null);
            if (limit != CompatibilityManager.UNLIMITED && entries.size() >= limit) return new Result(Status.LIMIT_REACHED, null);
            entries.put(home.id(), home);
            return new Result(Status.CREATED, home);
        });
    }

    @NotNull
    public CompletableFuture<Result> rename(@NotNull UUID owner, @NotNull UUID id, @NotNull String name) {
        HomeConfig config = this.config.get();
        if (!config.validName(name)) return CompletableFuture.completedFuture(new Result(Status.INVALID_NAME, null));
        String key = Home.key(name);
        long now = System.currentTimeMillis();
        return this.modify(owner, entries -> {
            Home current = entries.get(id);
            if (current == null) return new Result(Status.NOT_FOUND, null);
            Home duplicate = this.findByName(entries, key);
            if (duplicate != null && !duplicate.id().equals(id)) return new Result(Status.DUPLICATE_NAME, null);
            Home renamed = new Home(current.id(), current.owner(), name, current.server(), current.location(), current.createdAt(), now);
            entries.put(id, renamed);
            return new Result(Status.UPDATED, renamed);
        });
    }

    @NotNull
    public CompletableFuture<Result> relocate(@NotNull UUID owner, @NotNull UUID id, @NotNull String server, @NotNull WorldLocation location) {
        long now = System.currentTimeMillis();
        return this.modify(owner, entries -> {
            Home current = entries.get(id);
            if (current == null) return new Result(Status.NOT_FOUND, null);
            Home moved = new Home(current.id(), current.owner(), current.name(), server, location, current.createdAt(), now);
            entries.put(id, moved);
            return new Result(Status.UPDATED, moved);
        });
    }

    @NotNull
    public CompletableFuture<Result> delete(@NotNull UUID owner, @NotNull UUID id) {
        return this.modify(owner, entries -> {
            Home current = entries.get(id);
            if (current == null) return new Result(Status.NOT_FOUND, null);
            entries.remove(id);
            return new Result(Status.DELETED, current);
        });
    }

    @NotNull
    public CompletableFuture<Result> delete(@NotNull UUID owner, @NotNull String name) {
        String key = Home.key(name);
        return this.modify(owner, entries -> {
            Home current = this.findByName(entries, key);
            if (current == null) return new Result(Status.NOT_FOUND, null);
            entries.remove(current.id());
            return new Result(Status.DELETED, current);
        });
    }

    @NotNull
    public CompletableFuture<Long> deleteAll(@NotNull HomeFilter filter) {
        // 跨所有者操作以 null 登记, 模块停用等待整个批量删除及保存结束.
        return this.execute(filter.owner(), () -> filter.owner() == null
                ? this.deleteBatch(filter, null, 0)
                : this.deleteMatching(filter.owner(), filter));
    }

    private CompletableFuture<Long> deleteBatch(HomeFilter filter, @Nullable UUID after, long deleted) {
        return this.store.findOwners(filter, after, OWNER_BATCH_SIZE).thenCompose(owners -> {
            CompletableFuture<Long> deleting = CompletableFuture.completedFuture(deleted);
            for (int i = 0, size = owners.size(); i < size; i++) {
                UUID owner = owners.get(i);
                deleting = deleting.thenCompose(total -> this.deleteMatching(owner, filter).thenApply(count -> total + count));
            }
            if (owners.size() < OWNER_BATCH_SIZE) return deleting;
            // 删除会改变 SQL 结果集, 下一页从上一页末尾的所有者继续查询.
            UUID cursor = owners.getLast();
            return deleting.thenCompose(total -> this.deleteBatch(filter, cursor, total));
        });
    }

    private CompletableFuture<Long> deleteMatching(UUID owner, HomeFilter filter) {
        MultiWriterEntries<UUID, Home> entries = this.data.entries(owner);
        return entries.modify(edit -> {
            List<UUID> matching = new ArrayList<>();
            edit.forEach((id, home) -> {
                if (filter.matches(home)) {
                    matching.add(id);
                }
            });
            long deleted = 0;
            for (int i = 0, size = matching.size(); i < size; i++) {
                if (edit.remove(matching.get(i))) {
                    deleted++;
                }
            }
            return deleted;
        }).thenCompose(deleted -> deleted == 0
                ? CompletableFuture.completedFuture(0L)
                : entries.save().thenApply(ignored -> deleted));
    }

    private CompletableFuture<Result> modify(UUID owner, Function<EntryEdit<UUID, Home>, Result> operation) {
        return this.execute(owner, () -> {
            MultiWriterEntries<UUID, Home> entries = this.data.entries(owner);
            // modify 成功表示修改已提交到多写数据层, 成功结果还要等这一批数据落库.
            return entries.modify(operation).thenCompose(result -> switch (result.status()) {
                case CREATED, UPDATED, DELETED -> entries.save().thenApply(ignored -> result);
                case DUPLICATE_NAME, NOT_FOUND, INVALID_NAME, LIMIT_REACHED -> CompletableFuture.completedFuture(result);
            });
        });
    }

    @Nullable
    private Home findByName(EntryView<UUID, Home> entries, String key) {
        Home[] found = new Home[1];
        entries.forEach((id, home) -> {
            if (home.getNameKey().equals(key)) found[0] = home;
        });
        return found[0];
    }

    private <T> CompletableFuture<T> execute(@Nullable UUID owner, Supplier<CompletableFuture<T>> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (this) {
            if (!this.accepting) return CompletableFuture.failedFuture(new CancellationException("Home service is disabled"));
            this.active.computeIfAbsent(owner, ignored -> new HashSet<>()).add(result);
        }
        // 收尾等待完整业务操作, 包括 modify 完成后提交的 save.
        CompletableFuture.completedFuture(null).thenCompose(ignored -> operation.get()).whenComplete((value, error) -> {
            if (error == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(error);
            }
            synchronized (this) {
                Set<CompletableFuture<?>> operations = this.active.get(owner);
                operations.remove(result);
                if (operations.isEmpty()) this.active.remove(owner);
            }
        });
        return result.copy();
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onRelease(@NotNull PlayerConnection connection) {
        Set<CompletableFuture<?>> operations = this.active.get(connection.uniqueId());
        if (operations == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new)).handle((ignored, error) -> null);
    }

    synchronized CompletableFuture<Void> stop() {
        this.accepting = false;
        Set<CompletableFuture<?>> operations = new HashSet<>();
        this.active.values().forEach(operations::addAll);
        return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new)).handle((ignored, error) -> null);
    }

    public enum Status {
        CREATED, UPDATED, DELETED, DUPLICATE_NAME, NOT_FOUND, INVALID_NAME, LIMIT_REACHED
    }

    public record Result(@NotNull Status status, @Nullable Home home) {
    }
}
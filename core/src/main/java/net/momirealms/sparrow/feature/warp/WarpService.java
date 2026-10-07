package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class WarpService {
    private final WarpStore store;
    private final WarpRegistry registry;
    private final String serverId;
    private final WarpFeature feature;
    private final LongSupplier clock = System::currentTimeMillis;

    public WarpService() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        this.store = plugin.dataStorage().warpStore();
        this.feature = plugin.featureManager().feature(WarpFeature.ID, WarpFeature.class);
        this.registry = this.feature.registry();
        this.serverId = ServerConfig.serverId();
    }

    // 覆盖或新建一个 Warp.
    @NotNull
    public CompletableFuture<Result> set(@NotNull String name, @NotNull String server, @NotNull WorldLocation location, @Nullable UUID creator) {
        if (!this.validName(name)) return this.result(Status.INVALID_NAME);
        return this.store.findByName(name).thenCompose(existing -> {
            long now = this.clock.getAsLong();
            if (existing.isEmpty()) {
                Warp warp = new Warp(UUIDUtils.createV7(), name, "", server, location, creator, now, now);
                return this.save(this.store.create(warp), Status.CREATED);
            }
            if (!this.feature.config().overwriteExisting()) return this.result(Status.DUPLICATE_NAME);
            Warp warp = existing.get();
            return this.save(this.store.update(new Warp(warp.id(), name, warp.description(), server, location, warp.creator(), warp.createdAt(), now)), Status.UPDATED);
        });
    }

    // 按 Warp UUID 改名.
    @NotNull
    public CompletableFuture<Result> rename(@NotNull UUID id, @NotNull String name) {
        if (!this.validName(name)) return this.result(Status.INVALID_NAME);
        return this.edit(id, warp -> new Warp(warp.id(), name, warp.description(), warp.server(), warp.location(), warp.creator(), warp.createdAt(), this.clock.getAsLong()));
    }

    // 修改描述, 空字符串用于清空描述.
    @NotNull
    public CompletableFuture<Result> setDescription(@NotNull UUID id, @NotNull String description) {
        if (description.length() > Warp.MAX_DESCRIPTION_LENGTH) return this.result(Status.DESCRIPTION_TOO_LONG);
        return this.edit(id, warp -> new Warp(warp.id(), warp.name(), description, warp.server(), warp.location(), warp.creator(), warp.createdAt(), this.clock.getAsLong()));
    }

    // 修改服务器与位置.
    @NotNull
    public CompletableFuture<Result> relocate(@NotNull UUID id, @NotNull String server, @NotNull WorldLocation location) {
        return this.edit(id, warp -> new Warp(warp.id(), warp.name(), warp.description(), server, location, warp.creator(), warp.createdAt(), this.clock.getAsLong()));
    }

    private boolean validName(String name) {
        return name.length() <= Warp.MAX_NAME_LENGTH && Pattern.matches(this.feature.config().namePattern(), name);
    }

    private CompletableFuture<Result> edit(UUID id, UnaryOperator<Warp> edit) {
        return this.store.find(id).thenCompose(found -> found.isEmpty()
                ? this.result(Status.NOT_FOUND) : this.save(this.store.update(edit.apply(found.get())), Status.UPDATED));
    }

    private CompletableFuture<Result> result(Status status) {
        return CompletableFuture.completedFuture(new Result(status, null));
    }

    private CompletableFuture<Result> save(CompletableFuture<WarpStore.SaveResult> write, Status success) {
        return write.thenApply(saved -> {
            if (saved.status() != WarpStore.Status.SUCCESS) {
                Status status = switch (saved.status()) {
                    case DUPLICATE_NAME -> Status.DUPLICATE_NAME;
                    case NOT_FOUND -> Status.NOT_FOUND;
                    case SUCCESS -> throw new AssertionError();
                };
                return new Result(status, null);
            }
            this.registry.put(saved.warp());
            SparrowPlugin.instance().messageBrokerManager().publishOneWay(WarpMessage.save(this.serverId, saved.warp()), "");
            return new Result(success, saved.warp());
        });
    }

    // 删除指定 Warp
    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID id) {
        return this.store.delete(id).thenApply(deleted -> {
            if (deleted) {
                this.registry.remove(id);
                SparrowPlugin.instance().messageBrokerManager().publishOneWay(WarpMessage.delete(this.serverId, id), "");
            }
            return deleted;
        });
    }

    // 删除指定服务器和世界的记录, 返回数据库实际删除数量.
    @NotNull
    public CompletableFuture<Integer> deleteByWorld(@NotNull String server, @NotNull String world) {
        return this.store.deleteByWorld(server, world).thenCompose(this::reloadAfterBulkDelete);
    }

    // 删除指定服务器的记录, 返回数据库实际删除数量.
    @NotNull
    public CompletableFuture<Integer> deleteByServer(@NotNull String server) {
        return this.store.deleteByServer(server).thenCompose(this::reloadAfterBulkDelete);
    }

    private CompletableFuture<Integer> reloadAfterBulkDelete(int deleted) {
        if (deleted == 0) return CompletableFuture.completedFuture(0);
        SparrowPlugin.instance().messageBrokerManager().publishOneWay(WarpMessage.reload(this.serverId), "");
        return this.store.loadAll().thenApply(warps -> {
            this.registry.replaceAll(warps);
            return deleted;
        });
    }

    public enum Status {
        CREATED, UPDATED, DUPLICATE_NAME, NOT_FOUND, INVALID_NAME, DESCRIPTION_TOO_LONG
    }

    public record Result(@NotNull Status status, @Nullable Warp warp) {
    }
}

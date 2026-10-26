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

public final class WarpService {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final WarpFeature feature;
    private final WarpStore store;
    private final WarpRegistry registry;

    WarpService(WarpFeature feature, WarpStore store, WarpRegistry registry) {
        this.feature = feature;
        this.store = store;
        this.registry = registry;
    }

    @NotNull
    public CompletableFuture<Result> create(@NotNull String name, @NotNull String server, @NotNull WorldLocation location, @Nullable UUID creator) {
        if (!this.validName(name)) return CompletableFuture.completedFuture(new Result(Status.INVALID_NAME, null));
        long now = System.currentTimeMillis();
        Warp warp = new Warp(UUIDUtils.createV7(), name, server, location, creator, now, now);
        return this.saved(this.store.create(warp), warp.id(), Status.CREATED);
    }

    @NotNull
    public CompletableFuture<Result> rename(@NotNull UUID id, @NotNull String name) {
        if (!this.validName(name)) return CompletableFuture.completedFuture(new Result(Status.INVALID_NAME, id));
        return this.saved(this.store.rename(id, name, System.currentTimeMillis()), id, Status.UPDATED);
    }

    @NotNull
    public CompletableFuture<Result> relocate(@NotNull UUID id, @NotNull String server, @NotNull WorldLocation location) {
        return this.saved(this.store.relocate(id, server, location, System.currentTimeMillis()), id, Status.UPDATED);
    }

    private boolean validName(String name) {
        return !name.isEmpty() && name.length() <= Warp.MAX_NAME_LENGTH
                && name.codePoints().noneMatch(character -> Character.isWhitespace(character) || Character.isSpaceChar(character))
                && this.feature.config().namePattern().matcher(name).matches();
    }

    private CompletableFuture<Result> saved(CompletableFuture<WarpStore.Status> operation, UUID id, Status success) {
        return operation.thenCompose(status -> switch (status) {
            case SUCCESS -> this.committed(id).thenApply(ignored -> new Result(success, id));
            case DUPLICATE_NAME -> CompletableFuture.completedFuture(new Result(Status.DUPLICATE_NAME, id));
            case NOT_FOUND -> CompletableFuture.completedFuture(new Result(Status.NOT_FOUND, id));
        });
    }

    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID id) {
        return this.store.delete(id).thenCompose(deleted -> deleted
                ? this.committed(id).thenApply(ignored -> true)
                : CompletableFuture.completedFuture(false));
    }

    private CompletableFuture<Void> committed(UUID id) {
        CompletableFuture<Void> refreshed = this.registry.refresh(id);
        CompletableFuture<Long> published = this.plugin.messageBrokerManager().publishOneWay(new WarpMessage(ServerConfig.serverId(), id), "");
        return CompletableFuture.allOf(refreshed, published);
    }

    public enum Status {
        CREATED, UPDATED, DUPLICATE_NAME, NOT_FOUND, INVALID_NAME
    }

    public record Result(@NotNull Status status, @Nullable UUID id) {
    }
}
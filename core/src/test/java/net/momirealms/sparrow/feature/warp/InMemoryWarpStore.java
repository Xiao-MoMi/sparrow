package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.database.WarpStore;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// 以 id 为键的内存存储, 与数据库实现一样拒绝被其他 warp 占用的名称
final class InMemoryWarpStore implements WarpStore {
    final Map<UUID, Warp> warps = new HashMap<>();

    void put(Warp... warps) {
        for (Warp warp : warps) this.warps.put(warp.id(), warp);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Warp>> loadAll() {
        return CompletableFuture.completedFuture(this.warps.values().stream().sorted(Comparator.comparing(Warp::key)).toList());
    }

    @Override
    public CompletableFuture<Optional<Warp>> find(UUID id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(this.warps.get(id)));
    }

    @Override
    public CompletableFuture<Optional<Warp>> findByName(String name) {
        return CompletableFuture.completedFuture(this.warps.values().stream().filter(warp -> warp.key().equals(Warp.key(name))).findFirst());
    }

    @Override
    public CompletableFuture<Boolean> save(Warp warp) {
        boolean taken = this.warps.values().stream().anyMatch(other -> other.key().equals(warp.key()) && !other.id().equals(warp.id()));
        if (!taken) this.warps.put(warp.id(), warp);
        return CompletableFuture.completedFuture(!taken);
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID id) {
        return CompletableFuture.completedFuture(this.warps.remove(id) != null);
    }

    @Override
    public CompletableFuture<Integer> deleteByWorld(String server, String world) {
        return CompletableFuture.completedFuture(0);
    }

    @Override
    public CompletableFuture<Integer> deleteByServer(String server) {
        return CompletableFuture.completedFuture(0);
    }
}

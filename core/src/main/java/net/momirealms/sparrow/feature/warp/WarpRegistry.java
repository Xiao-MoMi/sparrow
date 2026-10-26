package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.database.WarpStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class WarpRegistry implements AutoCloseable {
    private final WarpStore store;
    private @Nullable Session session;

    WarpRegistry(WarpStore store) {
        this.store = store;
    }

    @NotNull
    synchronized CompletableFuture<Void> load() {
        Session loading = new Session();
        this.session = loading;
        this.store.loadAll().whenComplete((warps, error) -> {
            synchronized (this) {
                if (this.session != loading) return;
                if (error != null) {
                    this.session = null;
                    loading.ready.completeExceptionally(error);
                    for (Refresh refresh : loading.pending.values()) {
                        refresh.result().completeExceptionally(error);
                    }
                    loading.pending.clear();
                    return;
                }
                for (int i = 0, size = warps.size(); i < size; i++) {
                    Warp warp = warps.get(i);
                    loading.byId.put(warp.id(), warp);
                    loading.byName.put(warp.getNameKey(), warp);
                }
                loading.initializing = false;
            }
            this.drain(loading);
        });
        return loading.ready.copy();
    }

    @Nullable
    public synchronized Warp get(@NotNull UUID id) {
        return this.session == null ? null : this.session.byId.get(id);
    }

    @Nullable
    public synchronized Warp get(@NotNull String name) {
        return this.session == null ? null : this.session.byName.get(Warp.key(name));
    }

    @NotNull
    public synchronized List<Warp> all() {
        return this.session == null ? List.of() : List.copyOf(this.session.byName.values());
    }

    @NotNull
    public synchronized List<String> complete(@NotNull String input, @NotNull Predicate<Warp> visible) {
        if (this.session == null) return List.of();
        String prefix = Warp.key(input);
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Warp> entry : this.session.byName.tailMap(prefix).entrySet()) {
            if (!entry.getKey().startsWith(prefix)) break;
            if (visible.test(entry.getValue())) names.add(entry.getValue().name());
        }
        return names;
    }

    @NotNull
    CompletableFuture<Void> refresh(UUID id) {
        Session current;
        Refresh refresh;
        synchronized (this) {
            current = this.session;
            if (current == null) return CompletableFuture.completedFuture(null);
            refresh = current.pending.computeIfAbsent(id, key -> new Refresh(key, new CompletableFuture<>()));
        }
        this.drain(current);
        return refresh.result().copy();
    }

    private void drain(Session current) {
        Refresh refresh;
        synchronized (this) {
            if (this.session != current || current.initializing || current.reading != null) return;
            if (current.pending.isEmpty()) {
                current.ready.complete(null);
                return;
            }
            refresh = current.pending.pollFirstEntry().getValue();
            current.reading = refresh;
        }
        // 全量加载期间的变更与运行期变更共用队列, 查询开始顺序也是目录应用顺序.
        this.store.find(refresh.id()).whenComplete((warp, error) -> this.finish(current, refresh, warp, error));
    }

    private void finish(Session current, Refresh refresh, @Nullable Optional<Warp> found, @Nullable Throwable error) {
        synchronized (this) {
            if (this.session != current) return;
            current.reading = null;
            if (error == null) {
                Warp previous = current.byId.remove(refresh.id());
                if (previous != null) current.byName.remove(previous.getNameKey(), previous);
                if (found.isPresent()) {
                    Warp warp = found.get();
                    current.byId.put(warp.id(), warp);
                    current.byName.put(warp.getNameKey(), warp);
                }
            } else {
                current.ready.completeExceptionally(error);
            }
        }
        if (error == null) {
            refresh.result().complete(null);
        } else {
            refresh.result().completeExceptionally(error);
        }
        this.drain(current);
    }

    @Override
    public synchronized void close() {
        Session previous = this.session;
        if (previous == null) return;
        this.session = null;
        previous.byId.clear();
        previous.byName.clear();
        previous.ready.cancel(false);
        if (previous.reading != null) previous.reading.result().cancel(false);
        for (Refresh refresh : previous.pending.values()) {
            refresh.result().cancel(false);
        }
        previous.pending.clear();
    }

    private static final class Session {
        private final Map<UUID, Warp> byId = new HashMap<>();
        private final TreeMap<String, Warp> byName = new TreeMap<>();
        private final LinkedHashMap<UUID, Refresh> pending = new LinkedHashMap<>();
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private boolean initializing = true;
        private @Nullable Refresh reading;
    }

    private record Refresh(UUID id, CompletableFuture<Void> result) {
    }
}
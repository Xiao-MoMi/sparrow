package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class WarpRegistry implements AutoCloseable {
    private final WarpStore store;
    private final String serverId;
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile @Nullable Session session;

    public WarpRegistry() {
        this.store = SparrowPlugin.instance().dataStorage().warpStore();
        this.serverId = ServerConfig.serverId();
    }

    /**
     * 订阅变更并建立本次启用的完整目录.
     * <strong>会阻塞调用线程直到读取完成</strong>.
     */
    public void load() {
        Session session = new Session();
        synchronized (this) {
            this.session = session;
            this.snapshot = Snapshot.EMPTY;
            WarpMessage.listener(this::accept);
        }
        try {
            this.refresh().join();
        } catch (RuntimeException failure) {
            this.close(session);
            throw failure;
        }
    }

    @Nullable
    public Warp get(@NotNull String name) {
        return this.snapshot.byKey.get(Warp.key(name));
    }

    @Nullable
    public Warp get(@NotNull UUID id) {
        return this.snapshot.byId.get(id);
    }

    public int size() {
        return this.snapshot.warps.size();
    }

    @NotNull
    public List<Warp> all() {
        return this.snapshot.warps;
    }

    /**
     * 按前缀补全名称, 忽略大小写, 按名称键顺序最多返回 {@code limit} 个.
     *
     * @param visible 只有通过筛选的 warp 会出现在结果中
     */
    @NotNull
    public List<String> complete(@NotNull String input, @NotNull Predicate<Warp> visible, int limit) {
        Snapshot snapshot = this.snapshot;
        String prefix = Warp.key(input);
        int start = Collections.binarySearch(snapshot.keys, prefix);
        if (start < 0) {
            start = -start - 1;
        }
        List<String> result = new ArrayList<>(Math.min(limit, 16));
        int size = snapshot.keys.size();
        for (int i = start; i < size && result.size() < limit; i++) {
            if (!snapshot.keys.get(i).startsWith(prefix)) break;
            Warp warp = snapshot.warps.get(i);
            if (visible.test(warp)) {
                result.add(warp.name());
            }
        }
        return result;
    }

    /**
     * 处理其他服务器的变更通知, 在 Redis 线程上调用.
     */
    public void accept(@NotNull WarpMessage message) {
        if (message.origin().equals(this.serverId)) return;
        this.refresh(true);
    }

    @NotNull
    CompletableFuture<Void> refresh() {
        return this.refresh(false);
    }

    private CompletableFuture<Void> refresh(boolean reportFailure) {
        Session session;
        CompletableFuture<Void> future;
        synchronized (this) {
            session = this.session;
            if (session == null) return CompletableFuture.completedFuture(null);
            if (session.loading != null) {
                session.dirty = true;
                return session.loading.copy();
            }
            future = new CompletableFuture<>();
            session.loading = future;
        }
        this.read(session, future, reportFailure);
        return future.copy();
    }

    private void read(Session session, CompletableFuture<Void> future, boolean reportFailure) {
        synchronized (this) {
            if (this.session != session) return;
            session.dirty = false;
        }
        // 查询与快照构建在锁外执行, 发布时核对期间收到的变更.
        this.store.loadAll()
                .thenApply(Snapshot::of)
                .whenComplete((loaded, failure) -> {
                    boolean reread;
                    synchronized (this) {
                        if (this.session != session) return;
                        reread = failure == null && session.dirty;
                        if (!reread) {
                            session.loading = null;
                            if (failure == null) {
                                this.snapshot = loaded;
                            }
                        }
                    }
                    if (reread) {
                        this.read(session, future, reportFailure);
                    } else if (failure != null) {
                        future.completeExceptionally(failure);
                        if (reportFailure) {
                            SparrowPlugin.instance().logger().warn("Failed to refresh warp directory", failure);
                        }
                    } else {
                        future.complete(null);
                    }
                });
    }

    @Override
    public void close() {
        this.close(this.session);
    }

    private void close(@Nullable Session session) {
        CompletableFuture<Void> pending;
        synchronized (this) {
            if (session == null || this.session != session) return;
            WarpMessage.listener(null);
            this.session = null;
            this.snapshot = Snapshot.EMPTY;
            pending = session.loading;
        }
        if (pending != null) {
            pending.cancel(false);
        }
    }

    private static final class Session {
        private @Nullable CompletableFuture<Void> loading;
        private boolean dirty;
    }

    private record Snapshot(Map<UUID, Warp> byId, Map<String, Warp> byKey, List<Warp> warps, List<String> keys) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), List.of(), List.of());

        private static Snapshot of(List<Warp> values) {
            Map<UUID, Warp> byId = new HashMap<>();
            Map<String, Warp> byKey = new HashMap<>();
            int size = values.size();
            for (int i = 0; i < size; i++) {
                Warp warp = values.get(i);
                byId.put(warp.id(), warp);
                byKey.put(warp.key(), warp);
            }
            List<String> keys = byKey.keySet().stream().sorted().toList();
            List<Warp> warps = keys.stream().map(byKey::get).toList();
            return new Snapshot(Map.copyOf(byId), Map.copyOf(byKey), warps, keys);
        }
    }
}
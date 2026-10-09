package net.momirealms.sparrow.cluster;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.redis.heartbeat.RedisServerRegistry;
import net.momirealms.sparrow.redis.message.server.ServerChangedMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ServerDirectory {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final RedisServerRegistry registry = new RedisServerRegistry();
    private volatile Map<String, ServerStatus> snapshot = Map.of(); // 整体发布的不可变快照, 查询方直接读取
    private @Nullable CompletableFuture<Void> loading; // 非 null 表示全量刷新正在执行
    private @Nullable SchedulerTask task;
    private boolean closed;

    public void onLoad() {
        ServerChangedMessage.listener(this::accept);
        this.refresh().join();
        this.task = this.plugin.scheduler().asyncRepeating(() -> this.report(this.refresh()), 2, 2, TimeUnit.SECONDS);
    }

    @Nullable
    public ServerStatus get(@NotNull String serverId) {
        return this.snapshot.get(serverId);
    }

    public boolean isOnline(@NotNull String serverId) {
        return this.get(serverId) != null;
    }

    @NotNull
    public List<ServerStatus> getServers() {
        return this.snapshot.values().stream()
                .sorted(Comparator.comparing(ServerStatus::serverId))
                .toList();
    }

    // 本服发布者已主动刷新本地目录, 消息监听处理其他服务器的变更.
    private void accept(String serverId) {
        if (serverId.equals(ServerConfig.serverId())) return;
        this.report(this.refresh());
    }

    // 定时任务和消息回调在这里记录刷新失败, 关闭期间的取消由生命周期处理.
    private void report(CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, error) -> {
            synchronized (this) {
                if (this.closed) return;
            }
            if (error != null) {
                this.plugin.logger().warn("Failed to refresh server directory", error);
            }
        });
    }

    /**
     * 从 Redis 全量刷新目录, 刷新期间继续读取旧快照, 成功后整体替换; 查询失败时保留旧快照.
     * 刷新期间的调用共用本轮查询, 后续变化由下一轮刷新获取.
     *
     * @return 快照发布后完成的任务; 查询失败或目录已关闭时异常完成, 取消返回任务仅影响当前调用方
     */
    @NotNull
    public CompletableFuture<Void> refresh() {
        CompletableFuture<Void> future;
        synchronized (this) {
            if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Server directory is closed"));
            if (this.loading != null) return this.loading.copy();
            future = new CompletableFuture<>();
            this.loading = future;
        }
        this.registry.readAll().whenComplete((entries, error) -> {
            synchronized (this) {
                if (this.closed) return;
                if (error == null) {
                    this.snapshot = Map.copyOf(entries);
                }
                this.loading = null;
            }
            // 在锁外完成任务, 调用方的后续操作可独立进入目录.
            if (error == null) {
                future.complete(null);
            } else {
                future.completeExceptionally(error);
            }
        });
        return future.copy();
    }

    public void shutdown() {
        CompletableFuture<Void> pending;
        synchronized (this) {
            this.closed = true;
            if (this.task != null) this.task.cancel();
            ServerChangedMessage.listener(null);
            pending = this.loading;
            this.loading = null;
            this.snapshot = Map.of();
        }
        // 先封闭目录再取消等待者, 后到的 Redis 回调会按关闭状态结束.
        if (pending != null) {
            pending.cancel(false);
        }
    }
}
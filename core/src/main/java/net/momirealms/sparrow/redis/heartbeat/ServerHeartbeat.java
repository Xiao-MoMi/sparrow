package net.momirealms.sparrow.redis.heartbeat;

import net.momirealms.sparrow.cluster.ServerStatus;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.redis.message.server.ServerChangedMessage;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ServerHeartbeat {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final RedisServerRegistry registry = new RedisServerRegistry();
    private final long startedAt = System.currentTimeMillis();
    private String serverId;
    private @Nullable SchedulerTask renewal;
    private @Nullable SchedulerTask publication;
    private boolean claimed;   // 已取得身份, 关闭时需要释放
    private boolean published; // 已进入状态发布阶段, 后续周期任务同时更新状态
    private boolean closed;

    public void onLoad() {
        this.serverId = ServerConfig.serverId();
        if (!this.registry.claim(this.serverId).join()) {
            this.plugin.logger().error(LogConstants.SERVER_ID_DUPLICATE, this.serverId);
            throw new IllegalStateException("Server ID " + this.serverId + " is already registered in Redis");
        }
        synchronized (this) {
            this.claimed = true;
        }
        // 启动期间只续租身份, 可用目录由 onEnable 的延迟任务发布.
        this.renewal = this.plugin.scheduler().asyncRepeating(this::heartbeat, 2, 2, TimeUnit.SECONDS);
    }

    public synchronized void onEnable() {
        this.publication = this.plugin.scheduler().platform().runLater(this::publish, 1);
    }

    // 首次状态写入完成后刷新本地目录, 再通知其他服务器读取.
    private void publish() {
        CompletableFuture<Void> write;
        synchronized (this) {
            if (this.closed) return;
            this.published = true;
            write = this.registry.write(this.currentStatus());
        }
        this.report(write.thenCompose(ignored -> this.changed()));
    }

    // 启动期间续租身份, 发布阶段同时采集人数等信息并更新整份状态.
    private void heartbeat() {
        CompletableFuture<Void> write;
        synchronized (this) {
            if (this.closed) return;
            write = this.published ? this.registry.write(this.currentStatus()) : this.registry.renewIdentity(this.serverId);
        }
        this.report(write);
    }

    private ServerStatus currentStatus() {
        return new ServerStatus(this.serverId, this.startedAt, Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers(), Bukkit.getName());
    }

    // 周期任务和首次发布的异步失败在此记录, 关闭后的回调由停用流程收尾.
    private void report(CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, error) -> {
            synchronized (this) {
                if (this.closed) return;
            }
            if (error != null) {
                this.plugin.logger().warn("Failed to publish server heartbeat for " + this.serverId, error);
            }
        });
    }

    // 上线和正常下线共用通知流程, 本地目录刷新完成后通知其他服务器刷新目录.
    private CompletableFuture<Void> changed() {
        return this.plugin.serverDirectory().refresh()
                .thenCompose(ignored -> this.plugin.messageBrokerManager().publishOneWay(new ServerChangedMessage(this.serverId), ""))
                .thenApply(ignored -> null);
    }

    /**
     * 停止本服发布并释放所属记录, 等待注销和必要的变更通知完成.
     */
    public void shutdown() {
        CompletableFuture<Void> removal;
        boolean notify;
        synchronized (this) {
            this.closed = true;
            if (this.publication != null) this.publication.cancel();
            if (this.renewal != null) this.renewal.cancel();
            if (!this.claimed) return;
            this.claimed = false;
            notify = this.published;
            // 关闭标记与续期提交共用锁, 注销排在此前已提交的续期之后.
            removal = this.registry.remove(this.serverId);
        }
        // 在锁外等待, Redis 连接与消息代理需保留到本次注销完成.
        removal.thenCompose(ignored -> notify ? this.changed() : CompletableFuture.completedFuture(null)).join();
    }
}

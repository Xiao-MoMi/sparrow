package net.momirealms.sparrow.teleport;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.message.TeleportExecuteRequest;
import net.momirealms.sparrow.teleport.message.TeleportPrepareRequest;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.ui.Subscription;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class TeleportService {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final TeleportSource source = new TeleportSource();
    private final TeleportTarget target = new TeleportTarget();
    private final Object stateLock = new Object(); // 保护 running, 也让提交登记, 到达登记和关服互相串行
    private final ConcurrentChainedObject2ObjectHashTable<UUID, Object> localExecutions = new ConcurrentChainedObject2ObjectHashTable<>();
    private @Nullable Subscription quitSubscription;
    private boolean running;

    public void onEnable() {
        synchronized (this.stateLock) {
            this.running = true;
        }
        this.target.onEnable();
        this.quitSubscription = this.plugin.playerManager().registerQuitListener(player -> {
            Invulnerability.clear(player);
            return CompletableFuture.completedFuture(null);
        });
    }

    public boolean isExecutingLocally(@NotNull BukkitSparrowPlayer player) {
        return this.localExecutions.get(player.uniqueId()) != null;
    }

    boolean beginLocal(BukkitSparrowPlayer player, Object execution) {
        return this.localExecutions.putIfAbsent(player.uniqueId(), execution) == null;
    }

    void endLocal(BukkitSparrowPlayer player, Object execution) {
        this.localExecutions.remove(player.uniqueId(), execution);
    }

    // 处理传送请求消息
    @NotNull
    public CompletableFuture<TeleportPosition.Result> acceptTeleportPrepare(@NotNull TeleportPrepareRequest request) {
        return this.target.acceptTeleportPrepare(request);
    }

    // 处理传送执行消息
    @NotNull
    public CompletableFuture<TeleportResult> acceptTeleportExecute(@NotNull TeleportExecuteRequest request) {
        return this.target.acceptTeleportExecute(request);
    }

    public void shutdown() {
        // 停止接受新的传送
        synchronized (this.stateLock) {
            this.running = false;
        }
        // 以 TELEPORT_DISABLED 结束本服发起的传送和等待到达的玩家.
        this.source.shutdown();
        this.target.shutdown();
        this.localExecutions.clear();
        // 注销监听
        if (this.quitSubscription != null) {
            this.quitSubscription.close();
            this.quitSubscription = null;
        }
        // 清掉在线玩家身上由传送给的无敌.
        for (BukkitSparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            Invulnerability.clear(player);
        }
    }

    @NotNull
    TeleportSource source() {
        return this.source;
    }

    @NotNull
    TeleportTarget target() {
        return this.target;
    }

    @NotNull
    Object stateLock() {
        return this.stateLock;
    }

    // 调用方须持有 stateLock.
    boolean running() {
        return this.running;
    }
}
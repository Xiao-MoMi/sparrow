package net.momirealms.sparrow.teleport;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.redis.message.teleport.TeleportRequest;
import net.momirealms.sparrow.redis.proxy.ConnectResult;
import org.bukkit.event.Listener;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TeleportManager implements PlayerListener {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Cache<UUID, Arrival> arrivals;
    private @Nullable Listener arrivalListener;

    public TeleportManager() {
        this.arrivals = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build();
    }

    public void onEnable() {
        this.arrivalListener = VersionHelper.hasPaperPatch ? new PaperArrivalListener() : new SpigotArrivalListener();
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this.arrivalListener, this.plugin.javaPlugin());
        this.plugin.playerManager().registerListener(this);
    }

    /**
     * 把玩家送到指定服务器上的位置.
     * 本服直接传送, 其他服务器会先预留出生位置再请求代理切服.
     *
     * @return 传送结果, 落点预留或代理切服请求等待超过 5 秒时以 {@link TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TeleportResult> transfer(@NotNull Player player, @NotNull String server, @NotNull WorldLocation location) {
        if (ServerConfig.serverId().equals(server)) {
            Location destination = location.resolve();
            if (destination == null) {
                return CompletableFuture.completedFuture(TeleportResult.INVALID);
            }
            CompletableFuture<Boolean> teleport = VersionHelper.hasPaperPatch
                    ? player.teleportAsync(destination, TeleportCause.PLUGIN)
                    : CompletableFuture.supplyAsync(() -> player.teleport(destination, TeleportCause.PLUGIN), this.plugin.scheduler().platform());
            return teleport.thenApply(success -> success ? TeleportResult.LOCAL_SUCCESS : TeleportResult.FAILED);
        }
        if (!this.plugin.serverDirectory().isOnline(server)) {
            return CompletableFuture.completedFuture(TeleportResult.SERVER_OFFLINE);
        }
        return this.plugin.messageBrokerManager().broker()
                .publishTwoWay(new TeleportRequest(player.getUniqueId(), location), server)
                .orTimeout(5, TimeUnit.SECONDS)
                .thenCompose(response -> {
                    if (!response.accepted()) return CompletableFuture.completedFuture(TeleportResult.INVALID);
                    SparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
                    if (sparrow == null || !player.isOnline()) return CompletableFuture.completedFuture(TeleportResult.FAILED);
                    return sparrow.connect(server)
                            .thenApply(result -> result == ConnectResult.SUCCESS ? TeleportResult.REMOTE_SUCCESS : TeleportResult.FAILED);
                });
    }

    public boolean prepare(@NotNull UUID player, @NotNull WorldLocation location) {
        if (location.resolve() == null) return false;
        this.arrivals.put(player, new Arrival(location, false));
        return true;
    }

    @Nullable
    public Location consumeSpawn(@NotNull UUID player) {
        Arrival arrival = this.arrivals.asMap().remove(player);
        if (arrival == null) return null;
        Location location = arrival.location.resolve();
        if (location == null || arrival.invalid) {
            // 配置阶段还不能发送游戏聊天, 留到 Join 时提示.
            this.arrivals.put(player, new Arrival(arrival.location, true));
            return null;
        }
        return location;
    }

    @Override
    public void onJoin(@NotNull SparrowPlayer player) {
        Arrival arrival = this.arrivals.asMap().remove(player.uniqueId());
        if (arrival != null && arrival.invalid) {
            player.sendMessage(MessageConstants.COMMAND_TP_OFFLINE_INVALID);
        }
    }

    public void shutdown() {
        this.plugin.playerManager().unregisterListener(this);
        this.arrivals.invalidateAll();
    }

    private record Arrival(WorldLocation location, boolean invalid) {
    }
}

package net.momirealms.sparrow.player.teleport;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.command.parser.ServerParser;
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

public final class TeleportManager {
    private final SparrowPlugin plugin;
    private final Cache<UUID, Arrival> arrivals;

    public TeleportManager(@NotNull SparrowPlugin plugin) {
        this.plugin = plugin;
        this.arrivals = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build();
    }

    public void onEnable() {
        TeleportRequest.manager(this);
    }

    /**
     * 把玩家送到指定服务器上的位置.
     * 本服直接传送, 其他服务器会先预留出生位置再请求代理切服.
     *
     * @return 传送结果, 目标服务器 5 秒内没有应答时以 {@link TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TransferResult> transfer(@NotNull Player player, @NotNull String server, @NotNull WorldLocation location) {
        if (ServerConfig.serverId().equals(server)) {
            Location destination = location.resolve();
            if (destination == null) {
                return CompletableFuture.completedFuture(TransferResult.INVALID);
            }
            CompletableFuture<Boolean> teleport = VersionHelper.hasPaperPatch
                    ? player.teleportAsync(destination, TeleportCause.PLUGIN)
                    : CompletableFuture.supplyAsync(() -> player.teleport(destination, TeleportCause.PLUGIN), this.plugin.scheduler().platform());
            return teleport.thenApply(success -> success ? TransferResult.SUCCESS : TransferResult.FAILED);
        }
        return this.plugin.serverHeartBeats().isOnline(server).thenCompose(online -> {
                    if (!online) {
                        return CompletableFuture.completedFuture(TransferResult.SERVER_OFFLINE);
                    }
                    return this.plugin.messageBrokerManager()
                            .broker()
                            .publishTwoWay(new TeleportRequest(player.getUniqueId(), location), server)
                            .orTimeout(5, TimeUnit.SECONDS)
                            .thenApply(response -> {
                                if (!response.accepted()) {
                                    return TransferResult.INVALID;
                                }
                                if (!player.isOnline()) {
                                    return TransferResult.FAILED;
                                }
                                // 对方已预留出生位置, 经由玩家自己的连接请求代理切服
                                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                                out.writeUTF("Connect");
                                out.writeUTF(server);
                                player.sendPluginMessage(this.plugin.javaPlugin(), ServerParser.CHANNEL, out.toByteArray());
                                return TransferResult.CONNECTING;
                            });
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

    public void onJoin(@NotNull Player player) {
        Arrival arrival = this.arrivals.asMap().remove(player.getUniqueId());
        if (arrival != null && arrival.invalid) {
            SparrowPlayer receiver = this.plugin.playerManager().getPlayer(player);
            receiver.sendMessage(MessageConstants.COMMAND_TP_OFFLINE_INVALID);
        }
    }

    public void shutdown() {
        TeleportRequest.manager(null);
        this.arrivals.invalidateAll();
    }

    private record Arrival(WorldLocation location, boolean invalid) {
    }
}

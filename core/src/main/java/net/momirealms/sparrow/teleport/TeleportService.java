package net.momirealms.sparrow.teleport;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.redis.message.teleport.TeleportRequest;
import net.momirealms.sparrow.redis.proxy.ConnectResult;
import org.bukkit.event.HandlerList;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.configuration.TeleportConfig;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TeleportService implements Listener, PlayerListener {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, TeleportWarmup> warmups = new ConcurrentChainedObject2ObjectHashTable<>();
    private final Cache<UUID, Arrival> arrivals = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build();

    public void onEnable() {
        Listener arrivalListener = VersionHelper.hasPaperPatch ? new PaperArrivalListener() : new SpigotArrivalListener();
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(arrivalListener, this.plugin.javaPlugin());
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        this.plugin.playerManager().registerListener(this);
    }

    /**
     * 让玩家依次经过所属传送分组的处理器, 全部放行后送到目标位置.
     *
     * @return 传送结果, 落点预留或代理切服请求等待超过 5 秒时以 {@link TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TeleportResult> teleport(
            @NotNull Player player,
            @NotNull TeleportType type,
            @NotNull String server,
            @NotNull WorldLocation destination,
            boolean self
    ) {
        BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
        if (sparrow == null) return CompletableFuture.completedFuture(TeleportResult.FAILED);
        Teleport teleport = new Teleport(player.getUniqueId(), type, server, destination, self);
        TeleportGroup group = this.group(type);
        // 出发前处理器逐个执行, 前一个放行后才轮到下一个
        CompletableFuture<Component> denial = TeleportProcessor.PASS;
        for (TeleportProcessor.Pre processor : group.preProcessor()) {
            denial = denial.thenCompose(reason -> reason != null ? CompletableFuture.completedFuture(reason) : processor.before(sparrow, teleport));
        }
        return denial
                .thenCompose(reason -> {
                    if (reason == null) return this.transfer(player, server, destination);
                    if (!reason.equals(Component.empty())) sparrow.sendMessage(sparrow.render(reason));
                    return CompletableFuture.completedFuture(TeleportResult.REJECTED);
                })
                .thenApply(result -> {
                    // 到达后处理器只在本服到达时执行, 跨服到达发生在对方服务器上
                    BukkitSparrowPlayer arrived = result == TeleportResult.LOCAL_SUCCESS ? this.plugin.playerManager().getPlayer(player) : null;
                    if (arrived != null) {
                        for (TeleportProcessor.Post processor : group.postProcessor()) {
                            processor.arrived(arrived, teleport);
                        }
                    }
                    for (TeleportProcessor.Pre processor : group.preProcessor()) {
                        processor.finished(teleport, result);
                    }
                    return result;
                });
    }

    private TeleportGroup group(TeleportType type) {
        FeaturesConfig.ConfigDefinition features = this.plugin.configurationManager().featuresConfig().config();
        return TeleportConfig.group(switch (type) {
            case WARP -> features.warp().teleportGroup();
            case HOME -> features.home().teleportGroup();
            case BACK, DEATH_BACK -> features.back().teleportGroup();
            case BED -> features.bed().teleportGroup();
            case SPAWN -> features.spawn().teleportGroup();
        });
    }

    // 在玩家所属线程上开始预热, 结果的含义与出发前处理器相同. 同一玩家新的预热会替换旧的, 玩家已不在本服时静默取消
    @NotNull
    CompletableFuture<Component> warmup(@NotNull BukkitSparrowPlayer player, @NotNull WarmupProcessor options, int seconds) {
        CompletableFuture<Component> result = new CompletableFuture<>();
        this.plugin.scheduler().platform().run(() -> {
                    if (this.plugin.playerManager().getPlayer(player.platformPlayer()) != player) {
                        result.complete(Component.empty());
                        return;
                    }
                    TeleportWarmup warmup = new TeleportWarmup(this, player, options, seconds, result);
                    TeleportWarmup previous = this.warmups.put(player.uniqueId(), warmup);
                    if (previous != null) {
                        previous.cancel(null);
                    }
                    warmup.start();
                }, () -> result.complete(Component.empty()), player.platformPlayer());
        return result;
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
                    BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
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

    // 受伤取消预热
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        TeleportWarmup warmup = this.warmups.get(player.getUniqueId());
        if (warmup != null && warmup.cancelOnDamage()) {
            warmup.cancel(MessageConstants.TELEPORT_CANCELLED_DAMAGED);
        }
    }

    @Override
    public void onJoin(@NotNull BukkitSparrowPlayer player) {
        Arrival arrival = this.arrivals.asMap().remove(player.uniqueId());
        if (arrival != null && arrival.invalid) {
            player.sendMessage(MessageConstants.COMMAND_TP_OFFLINE_INVALID);
        }
    }

    // 离开本服取消预热
    @Override
    public void onQuit(@NotNull BukkitSparrowPlayer player) {
        TeleportWarmup warmup = this.warmups.get(player.uniqueId());
        if (warmup != null) warmup.cancel(null);
    }

    public void shutdown() {
        this.plugin.playerManager().unregisterListener(this);
        HandlerList.unregisterAll(this);
        for (TeleportWarmup warmup : this.warmups.values()) {
            warmup.cancel(null);
        }
        this.arrivals.invalidateAll();
    }

    void finished(@NotNull UUID player, @NotNull TeleportWarmup warmup) {
        this.warmups.remove(player, warmup);
    }

    @NotNull
    SparrowPlugin plugin() {
        return this.plugin;
    }

    private record Arrival(WorldLocation location, boolean invalid) {
    }
}
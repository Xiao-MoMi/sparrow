package net.momirealms.sparrow.teleport;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.redis.message.teleport.TeleportRequest;
import net.momirealms.sparrow.redis.message.teleport.TeleportResponse;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TeleportService implements Listener, PlayerListener {
    private static final TeleportGroup DIRECT = new TeleportGroup(); // 直接传送使用的空分组

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
        List<TeleportProcessor.Pre> processors = this.group(type).preProcessor();
        // 出发前处理器逐个执行, 前一个放行后才轮到下一个
        CompletableFuture<Component> denial = TeleportProcessor.PASS;
        for (TeleportProcessor.Pre processor : processors) {
            denial = denial.thenCompose(reason -> reason != null ? CompletableFuture.completedFuture(reason) : processor.before(sparrow, teleport));
        }
        return denial
                .thenCompose(reason -> reason != null ? CompletableFuture.completedFuture(this.reject(player, reason)) : this.transfer(player, teleport))
                .thenApply(result -> {
                    for (TeleportProcessor.Pre processor : processors) {
                        processor.finished(teleport, result);
                    }
                    return result;
                });
    }

    private TeleportGroup group(@Nullable TeleportType type) {
        if (type == null) return DIRECT;
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
     * 不经过任何处理器, 直接把玩家送到指定服务器上的位置.
     *
     * @return 传送结果, 落点预留或代理切服请求等待超过 5 秒时以 {@link TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TeleportResult> transfer(@NotNull Player player, @NotNull String server, @NotNull WorldLocation location) {
        return this.transfer(player, new Teleport(player.getUniqueId(), null, server, location, false));
    }

    // 本服直接传送, 其他服务器会先请对方预留落点再请求代理切服. 落点处理器由落点所在的服务器执行
    private CompletableFuture<TeleportResult> transfer(Player player, Teleport teleport) {
        String server = teleport.server();
        if (ServerConfig.serverId().equals(server)) {
            return this.destination(teleport).thenCompose(reason -> {
                if (reason != null) return CompletableFuture.completedFuture(this.reject(player, reason));
                Location destination = teleport.location().resolve();
                if (destination == null) return CompletableFuture.completedFuture(TeleportResult.INVALID);
                CompletableFuture<Boolean> moved = VersionHelper.hasPaperPatch
                        ? player.teleportAsync(destination, TeleportCause.PLUGIN)
                        : CompletableFuture.supplyAsync(() -> player.teleport(destination, TeleportCause.PLUGIN), this.plugin.scheduler().platform());
                return moved.thenApply(success -> {
                    if (!success) return TeleportResult.FAILED;
                    BukkitSparrowPlayer arrived = this.plugin.playerManager().getPlayer(player);
                    if (arrived != null) this.arrived(arrived, teleport);
                    return TeleportResult.LOCAL_SUCCESS;
                });
            });
        }
        if (!this.plugin.serverDirectory().isOnline(server)) {
            return CompletableFuture.completedFuture(TeleportResult.SERVER_OFFLINE);
        }
        return this.plugin.messageBrokerManager().broker()
                .publishTwoWay(new TeleportRequest(teleport), server)
                .orTimeout(5, TimeUnit.SECONDS)
                .thenCompose(response -> {
                    Component denial = response.denial();
                    if (denial != null) return CompletableFuture.completedFuture(this.reject(player, denial));
                    if (!response.accepted()) return CompletableFuture.completedFuture(TeleportResult.INVALID);
                    BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
                    if (sparrow == null || !player.isOnline()) return CompletableFuture.completedFuture(TeleportResult.FAILED);
                    return sparrow.connect(server)
                            .thenApply(result -> result == ConnectResult.SUCCESS ? TeleportResult.REMOTE_SUCCESS : TeleportResult.FAILED);
                });
    }

    /**
     * 为其他服务器送来的玩家执行落点处理器, 全部放行后预留落点等玩家进服.
     *
     * @return 给来源服务器的回应, 被拒绝时带有给玩家的提示
     */
    @NotNull
    public CompletableFuture<TeleportResponse> prepare(@NotNull Teleport teleport) {
        return this.destination(teleport).thenApply(reason -> {
            if (reason != null) return new TeleportResponse(false, reason);
            if (teleport.location().resolve() == null) return new TeleportResponse(false, null);
            this.arrivals.put(teleport.player(), new Arrival(teleport, false));
            return new TeleportResponse(true, null);
        });
    }

    // 落点处理器逐个执行, 结果的含义与出发前处理器相同
    private CompletableFuture<Component> destination(Teleport teleport) {
        List<TeleportProcessor.Target> processors = this.group(teleport.type()).targetProcessor();
        if (processors.isEmpty()) return TeleportProcessor.PASS;
        return this.plugin.playerLookup().resolvePlayer(teleport.player()).thenCompose(found -> {
            SparrowPlayer player = found.orElseThrow();
            CompletableFuture<Component> denial = TeleportProcessor.PASS;
            for (TeleportProcessor.Target processor : processors) {
                denial = denial.thenCompose(reason -> reason != null ? CompletableFuture.completedFuture(reason) : processor.destination(player, teleport));
            }
            return denial;
        });
    }

    private void arrived(BukkitSparrowPlayer player, Teleport teleport) {
        for (TeleportProcessor.Post processor : this.group(teleport.type()).postProcessor()) {
            processor.arrived(player, teleport);
        }
    }

    // 把拒绝的原因提示给被传送的玩家, 空组件表示处理器已经自行提示
    private TeleportResult reject(Player player, Component reason) {
        BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
        if (sparrow != null && !reason.equals(Component.empty())) sparrow.sendMessage(sparrow.render(reason));
        return TeleportResult.REJECTED;
    }

    @Nullable
    public Location getSpawnLocation(@NotNull UUID player) {
        Arrival arrival = this.arrivals.getIfPresent(player);
        if (arrival == null || arrival.invalid) return null;
        Location location = arrival.teleport.location().resolve();
        // 记录留到 Join 再处理: 配置阶段还不能发送游戏聊天, 到达后处理器也要等玩家进入世界
        this.arrivals.put(player, new Arrival(arrival.teleport, location == null));
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
        if (arrival == null) return;
        if (arrival.invalid) {
            player.sendMessage(MessageConstants.COMMAND_TP_OFFLINE_INVALID);
        } else {
            this.arrived(player, arrival.teleport);
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

    // invalid 表示玩家进服时落点所在的世界已经不存在
    private record Arrival(Teleport teleport, boolean invalid) {
    }
}
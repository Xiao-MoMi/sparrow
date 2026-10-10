package net.momirealms.sparrow.teleport;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.SetArgs;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.redis.message.teleport.TeleportRequest;
import net.momirealms.sparrow.redis.proxy.ConnectResult;
import org.bukkit.event.HandlerList;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
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

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TeleportService implements Listener, PlayerListener {
    private static final String COOLDOWN_PREFIX = "sparrow:teleport-cooldown:"; // 后接传送类型与玩家 UUID, 过期即冷却结束

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
     * 按参数检查冷却、原地预热后把玩家送到目标位置.
     *
     * @return 传送结果, 落点预留或代理切服请求等待超过 5 秒时以 {@link TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TeleportResult> teleport(
            @NotNull Player player,
            @NotNull String server,
            @NotNull WorldLocation destination,
            @NotNull TeleportOptions options
    ) {
        CompletableFuture<Long> remaining = options.cooldownSeconds() > 0 ? this.remainingCooldown(player.getUniqueId(), options.type())
                : CompletableFuture.completedFuture(0L);
        return remaining.thenCompose(millis -> {
            // 正在冷却
            if (millis > 0) {
                BukkitSparrowPlayer receiver = this.plugin.playerManager().getPlayer(player);
                if (receiver != null) receiver.sendMessage(MessageConstants.TELEPORT_COOLDOWN, Component.text((millis + 999) / 1000));
                return CompletableFuture.completedFuture(TeleportResult.COOLDOWN);
            }
            // 没有倒计时
            if (options.warmupSeconds() <= 0) {
                return this.transfer(player, server, destination, options);
            }
            // 开始倒计时预热
            return this.warmup(player, options)
                    .thenCompose(finished ->
                            finished
                            ? this.transfer(player, server, destination, options)
                            : CompletableFuture.completedFuture(TeleportResult.CANCELLED)
                    );
        });
    }

    // 在玩家所属线程上开始预热, 结果为是否完整走完; 同一玩家新的预热会替换旧的, 玩家已不在本服时视为取消
    private CompletableFuture<Boolean> warmup(Player player, TeleportOptions options) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        this.plugin.scheduler().platform().run(() -> {
                    BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
                    if (sparrow == null) {
                        result.complete(false);
                        return;
                    }
                    TeleportWarmup warmup = new TeleportWarmup(this, sparrow, options, result);
                    TeleportWarmup previous = this.warmups.put(player.getUniqueId(), warmup);
                    if (previous != null) {
                        previous.cancel(null);
                    }
                    warmup.start();
                }, () -> result.complete(false), player);
        return result;
    }

    // 本服传送成功或代理确认切服成功后开始冷却.
    private CompletableFuture<TeleportResult> transfer(Player player, String server, WorldLocation destination, TeleportOptions options) {
        return this.transfer(player, server, destination)
                .thenApply(result -> {
                    if (options.cooldownSeconds() > 0 && (result == TeleportResult.LOCAL_SUCCESS || result == TeleportResult.REMOTE_SUCCESS)) {
                        this.startCooldown(player.getUniqueId(), options);
                    }
                    // 到达音效只在本服到达时播放, 跨服到达发生在对方服务器上
                    if (result == TeleportResult.LOCAL_SUCCESS) {
                        Sound sound = options.completeSound();
                        BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
                        if (sound != null && sparrow != null) {
                            sparrow.playSound(sound);
                        }
                    }
                    return result;
                });
    }

    // 记录冷却到 Redis
    private void startCooldown(UUID player, TeleportOptions options) {
        this.plugin.redisConnector().connection().async().set(cooldownKey(player, options.type()), new byte[]{1}, SetArgs.Builder.px(options.cooldownSeconds() * 1000L));
    }

    // 读取剩余冷却毫秒数, 没有冷却时为 0
    private CompletableFuture<Long> remainingCooldown(UUID player, TeleportType type) {
        return this.plugin.redisConnector().connection().async().pttl(cooldownKey(player, type)).toCompletableFuture().thenApply(millis -> Math.max(millis, 0L));
    }

    // 功能冷却 KEY
    private static byte[] cooldownKey(UUID player, TeleportType type) {
        return (COOLDOWN_PREFIX + type.id() + ":" + player).getBytes(StandardCharsets.UTF_8);
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
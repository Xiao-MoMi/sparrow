package net.momirealms.sparrow.player.teleport;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.lettuce.core.SetArgs;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TeleportService implements Listener {
    private static final String COOLDOWN_PREFIX = "sparrow:teleport-cooldown:"; // 后接传送类型与玩家 UUID, 过期即冷却结束

    private final SparrowPlugin plugin;
    private final TeleportManager teleports;
    private final ConcurrentChainedObject2ObjectHashTable<UUID, TeleportWarmup> warmups = new ConcurrentChainedObject2ObjectHashTable<>();

    public TeleportService(@NotNull SparrowPlugin plugin, @NotNull TeleportManager teleports) {
        this.plugin = plugin;
        this.teleports = teleports;
    }

    /**
     * 按参数检查冷却、原地预热后把玩家送到目标位置.
     *
     * @return 传送结果, 目标服务器 5 秒内没有应答时以 {@link java.util.concurrent.TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TeleportResult> teleport(@NotNull Player player,
                                                      @NotNull String server,
                                                      @NotNull WorldLocation destination,
                                                      @NotNull TeleportOptions options) {
        CompletableFuture<Long> remaining = options.cooldownSeconds() > 0 ? this.remainingCooldown(player.getUniqueId(), options.type()) : CompletableFuture.completedFuture(0L);
        return remaining.thenCompose(millis -> {
            // 正在冷却
            if (millis > 0) {
                SparrowPlayer receiver = this.plugin.playerManager().getPlayer(player);
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
            SparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
            if (sparrow == null) {
                result.complete(false);
                return;
            }
            TeleportWarmup warmup = new TeleportWarmup(this, sparrow, options, result);
            TeleportWarmup previous = this.warmups.put(player.getUniqueId(), warmup);
            if (previous != null) previous.cancel(null);
            warmup.start();
        }, () -> result.complete(false), player);
        return result;
    }

    // 传送成功或开始切服后才开始冷却
    private CompletableFuture<TeleportResult> transfer(Player player, String server, WorldLocation destination, TeleportOptions options) {
        return this.teleports.transfer(player, server, destination).thenApply(result -> {
            if (options.cooldownSeconds() > 0 && (result == TransferResult.SUCCESS || result == TransferResult.CONNECTING)) {
                this.startCooldown(player.getUniqueId(), options);
            }
            // 到达音效只在本服到达时播放, 跨服到达发生在对方服务器上
            if (result == TransferResult.SUCCESS) {
                Sound sound = PluginConfig.teleport().completeSound();
                SparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
                if (sound != null && sparrow != null) sparrow.playSound(sound);
            }
            return TeleportResult.of(result);
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

    // 受伤取消预热
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        TeleportWarmup warmup = this.warmups.get(player.getUniqueId());
        if (warmup != null && warmup.cancelOnDamage())
            warmup.cancel(MessageConstants.TELEPORT_CANCELLED_DAMAGED);
    }

    // 离开本服取消预热
    public void onQuit(@NotNull UUID player) {
        TeleportWarmup warmup = this.warmups.get(player);
        if (warmup != null) warmup.cancel(null);
    }

    public void shutdown() {
        for (TeleportWarmup warmup : this.warmups.values()) warmup.cancel(null);
    }

    void finished(@NotNull UUID player, @NotNull TeleportWarmup warmup) {
        this.warmups.remove(player, warmup);
    }

    @NotNull
    SparrowPlugin plugin() {
        return this.plugin;
    }
}

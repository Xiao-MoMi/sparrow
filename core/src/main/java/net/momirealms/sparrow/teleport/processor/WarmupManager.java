package net.momirealms.sparrow.teleport.processor;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WarmupManager implements Listener, PlayerListener {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, TeleportWarmup> warmups = new ConcurrentChainedObject2ObjectHashTable<>();

    public void onEnable() {
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        this.plugin.playerManager().registerListener(this);
    }

    // 在玩家所属线程上开始预热, 结果的含义与出发前处理器相同. 同一玩家新的预热会替换旧的, 玩家已不在本服时静默取消
    @NotNull
    CompletableFuture<Component> start(@NotNull BukkitSparrowPlayer player, @NotNull WarmupProcessor options, int seconds) {
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

    void finished(@NotNull UUID player, @NotNull TeleportWarmup warmup) {
        this.warmups.remove(player, warmup);
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
    }
}
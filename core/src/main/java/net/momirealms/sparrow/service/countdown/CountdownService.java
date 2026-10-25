package net.momirealms.sparrow.service.countdown;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.service.countdown.CountdownCancelledException.Reason;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.ui.Subscription;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CountdownService implements Listener {
    final ConcurrentChainedObject2ObjectHashTable<UUID, Countdown> countdowns = new ConcurrentChainedObject2ObjectHashTable<>(); // 每名玩家正在进行的倒计时
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private @Nullable Subscription quitSubscription;

    public void onEnable() {
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        this.quitSubscription = this.plugin.playerManager().registerQuitListener(player -> {
            Countdown countdown = this.countdowns.get(player.uniqueId());
            if (countdown != null) countdown.cancel(Reason.QUIT);
            return CompletableFuture.completedFuture(null);
        });
    }

    @NotNull
    public CompletableFuture<Void> start(@NotNull BukkitSparrowPlayer player, @NotNull CountdownSettings settings, @NotNull TranslatableComponent text) {
        if (settings.duration().isZero()) return CompletableFuture.completedFuture(null);
        Countdown countdown = new Countdown(this, player, settings, text);
        this.plugin.scheduler().platform().run(countdown::start, () -> countdown.cancel(Reason.QUIT), player.platformPlayer);
        return countdown.result;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Countdown countdown = this.countdowns.get(player.getUniqueId());
        if (countdown != null && countdown.settings.cancelOnDamage()) countdown.cancel(Reason.DAMAGED);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (this.quitSubscription != null) {
            this.quitSubscription.close();
            this.quitSubscription = null;
        }
    }
}
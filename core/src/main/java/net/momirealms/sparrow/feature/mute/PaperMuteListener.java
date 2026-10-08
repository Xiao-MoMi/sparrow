package net.momirealms.sparrow.feature.mute;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

final class PaperMuteListener implements Listener {
    private final MuteFeature feature;

    PaperMuteListener(@NotNull MuteFeature feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        TranslatableComponent denial = this.feature.denial(event.getPlayer());
        if (denial == null) return;
        event.setCancelled(true);
        this.feature.reply(event.getPlayer(), denial);
    }
}
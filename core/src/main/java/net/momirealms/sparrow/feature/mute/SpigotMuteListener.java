package net.momirealms.sparrow.feature.mute;

import org.bukkit.event.player.AsyncPlayerChatEvent;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

final class SpigotMuteListener implements Listener {
    private final MuteFeature feature;

    SpigotMuteListener(@NotNull MuteFeature feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(@NotNull AsyncPlayerChatEvent event) {
        TranslatableComponent denial = this.feature.denial(event.getPlayer());
        if (denial == null) return;
        event.setCancelled(true);
        this.feature.reply(event.getPlayer(), denial);
    }
}
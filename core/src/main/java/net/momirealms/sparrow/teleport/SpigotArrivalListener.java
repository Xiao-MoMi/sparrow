package net.momirealms.sparrow.teleport;

import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("all")
final class SpigotArrivalListener implements Listener {
    private final TeleportManager manager = SparrowPlugin.instance().teleportManager();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(@NotNull PlayerSpawnLocationEvent event) {
        Location location = this.manager.consumeSpawn(event.getPlayer().getUniqueId());
        if (location != null) {
            event.setSpawnLocation(location);
        }
    }
}
package net.momirealms.sparrow.teleport;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

final class PaperArrivalListener implements Listener {
    private final TeleportManager manager = SparrowPlugin.instance().teleportManager();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(@NotNull AsyncPlayerSpawnLocationEvent event) {
        Location location = this.manager.consumeSpawn(event.getConnection().getProfile().getId());
        if (location != null) {
            event.setSpawnLocation(location);
        }
    }
}
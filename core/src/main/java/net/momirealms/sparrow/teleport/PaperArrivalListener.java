package net.momirealms.sparrow.teleport;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.minecraft.network.Connection;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.proxy.paper.connection.ReadablePlayerCookieConnectionProxy;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

final class PaperArrivalListener implements Listener {
    private final TeleportService service = SparrowPlugin.instance().teleportService();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(@NotNull AsyncPlayerSpawnLocationEvent event) {
        Connection connection = (Connection) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection());
        Location location = this.service.selectSpawn(event.getConnection().getProfile().getId(), connection.channel);
        if (location != null) {
            event.setSpawnLocation(location);
        }
    }
}
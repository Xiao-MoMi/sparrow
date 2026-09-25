package net.momirealms.sparrow.player;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.netty.channel.ChannelHandler;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionReconfigureEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Location;
import net.momirealms.sparrow.proxy.paper.connection.ReadablePlayerCookieConnectionProxy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("all")
final class PaperLoginListener implements Listener {
    private final PlayerManager manager;

    PaperLoginListener(@NotNull PlayerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInitialConfigure(@NotNull PlayerConnectionInitialConfigureEvent event) {
        this.registerConnection(event.getConnection());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReconfigure(@NotNull PlayerConnectionReconfigureEvent event) {
        this.registerConnection(event.getConnection());
    }

    private void registerConnection(@NotNull PlayerConfigurationConnection connection) {
        PlayerProfile profile = connection.getProfile();
        this.manager.registerConnection((ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(connection), profile.getId(), profile.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(@NotNull AsyncPlayerSpawnLocationEvent event) {
        Location location = this.manager.teleports().consumeSpawn(event.getConnection().getProfile().getId());
        if (location != null) event.setSpawnLocation(location);
    }
}

package net.momirealms.sparrow.player;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.netty.channel.ChannelHandler;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionReconfigureEvent;
import net.momirealms.sparrow.proxy.paper.connection.ReadablePlayerCookieConnectionProxy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

final class PaperPlayerListener implements Listener {
    private final PlayerManager manager;

    PaperPlayerListener(@NotNull PlayerManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInitialConfigure(@NotNull PlayerConnectionInitialConfigureEvent event) {
        this.createPlayer(event.getConnection());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReconfigure(@NotNull PlayerConnectionReconfigureEvent event) {
        this.createPlayer(event.getConnection());
    }

    private void createPlayer(@NotNull PlayerConfigurationConnection connection) {
        PlayerProfile profile = connection.getProfile();
        this.manager.createPlayer((ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(connection), profile.getId(), profile.getName());
    }
}

package net.momirealms.sparrow.player;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.netty.channel.ChannelHandler;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionReconfigureEvent;
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
        PlayerProfile profile = event.getConnection().getProfile();
        this.manager.registerConnection(
                (ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection()),
                profile.getId(),
                profile.getName()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReconfigure(@NotNull PlayerConnectionReconfigureEvent event) {
        PlayerProfile profile = event.getConnection().getProfile();
        this.manager.registerConnection(
                (ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection()),
                profile.getId(),
                profile.getName()
        );
    }
}
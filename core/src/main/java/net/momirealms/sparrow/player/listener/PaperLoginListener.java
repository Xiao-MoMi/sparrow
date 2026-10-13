package net.momirealms.sparrow.player.listener;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.netty.channel.ChannelHandler;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionReconfigureEvent;
import net.momirealms.sparrow.proxy.paper.connection.ReadablePlayerCookieConnectionProxy;
import net.momirealms.sparrow.util.TriConsumer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@SuppressWarnings("all")
public final class PaperLoginListener implements Listener {
    private final TriConsumer<ChannelHandler, UUID, String> register;

    public PaperLoginListener(@NotNull TriConsumer<ChannelHandler, UUID, String> register) {
        this.register = register;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInitialConfigure(@NotNull PlayerConnectionInitialConfigureEvent event) {
        PlayerProfile profile = event.getConnection().getProfile();
        this.register.accept(
                (ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection()),
                profile.getId(),
                profile.getName()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReconfigure(@NotNull PlayerConnectionReconfigureEvent event) {
        PlayerProfile profile = event.getConnection().getProfile();
        this.register.accept(
                (ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection()),
                profile.getId(),
                profile.getName()
        );
    }
}
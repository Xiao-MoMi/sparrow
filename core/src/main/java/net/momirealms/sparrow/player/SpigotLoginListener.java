package net.momirealms.sparrow.player;

import io.netty.channel.ChannelHandler;
import net.momirealms.sparrow.proxy.bukkit.entity.CraftPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.network.ServerLoginPacketListenerImplProxy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;

final class SpigotLoginListener implements Listener {
    private final PlayerManager manager;

    SpigotLoginListener(@NotNull PlayerManager manager) {
        this.manager = manager;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(@NotNull PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        Player player = event.getPlayer();
        Object handle = CraftPlayerProxy.INSTANCE.getHandle(player);
        Object listener = ServerPlayerProxy.INSTANCE.getTransferCookieConnection(handle);
        ChannelHandler connection = (ChannelHandler) ServerLoginPacketListenerImplProxy.INSTANCE.getConnection(listener);
        this.manager.registerConnection(connection, player.getUniqueId(), player.getName());
    }
}

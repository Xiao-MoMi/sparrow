package net.momirealms.sparrow.teleport;

import net.minecraft.network.Connection;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.network.ServerCommonPacketListenerImplProxy;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

@SuppressWarnings("removal")
final class SpigotArrivalListener implements Listener {
    private final TeleportService service = SparrowPlugin.instance().teleportService();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(@NotNull PlayerSpawnLocationEvent event) {
        // 出生点事件发生在游戏连接建立之前, 此时使用配置阶段的连接
        Object listener = ServerPlayerProxy.INSTANCE.getTransferCookieConnection(((CraftPlayer) event.getPlayer()).getHandle());
        Connection connection = (Connection) ServerCommonPacketListenerImplProxy.INSTANCE.getConnection(listener);
        Location location = this.service.selectSpawn(event.getPlayer().getUniqueId(), connection.channel);
        if (location != null) {
            event.setSpawnLocation(location);
        }
    }
}
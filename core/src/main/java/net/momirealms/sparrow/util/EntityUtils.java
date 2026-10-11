package net.momirealms.sparrow.util;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.teleport.TeleportResult;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class EntityUtils {

    private EntityUtils() {
    }

    @NotNull
    public static CompletableFuture<Boolean> teleport(@NotNull Entity entity, @NotNull Location location) {
        if (VersionHelper.hasFoliaPatch) {
            if (entity instanceof Player player) {
                return SparrowPlugin.instance().teleportService()
                        .transfer(player, ServerConfig.serverId(), WorldLocation.from(location))
                        .thenApply(result -> result == TeleportResult.LOCAL_SUCCESS);
            }
            return entity.teleportAsync(location, TeleportCause.PLUGIN);
        }
        return CompletableFuture.completedFuture(entity.teleport(location, TeleportCause.PLUGIN));
    }

    @NotNull
    public static CompletableFuture<Boolean> rotate(@NotNull Entity entity, @NotNull Location location) {
        if (entity instanceof Player) return teleport(entity, location);
        entity.setRotation(location.getYaw(), location.getPitch());
        return CompletableFuture.completedFuture(true);
    }
}

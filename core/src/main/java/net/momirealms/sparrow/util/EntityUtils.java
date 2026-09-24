package net.momirealms.sparrow.util;

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
        if (VersionHelper.isFolia()) return entity.teleportAsync(location, TeleportCause.PLUGIN);
        return CompletableFuture.completedFuture(entity.teleport(location, TeleportCause.PLUGIN));
    }

    @NotNull
    public static CompletableFuture<Boolean> rotate(@NotNull Entity entity, @NotNull Location location) {
        if (entity instanceof Player) return teleport(entity, location);
        entity.setRotation(location.getYaw(), location.getPitch());
        return CompletableFuture.completedFuture(true);
    }
}

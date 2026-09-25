package net.momirealms.sparrow.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record WorldLocation(@NotNull String world, double x, double y, double z, float yaw, float pitch) {

    @NotNull
    public static WorldLocation from(@NotNull Location location) {
        return new WorldLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    @Nullable
    public Location resolve() {
        World loaded = Bukkit.getWorld(this.world);
        if (loaded == null || !Double.isFinite(this.x) || !Double.isFinite(this.y) || !Double.isFinite(this.z)
                || !Float.isFinite(this.yaw) || !Float.isFinite(this.pitch)) return null;
        return new Location(loaded, this.x, this.y, this.z, this.yaw, this.pitch);
    }
}

package net.momirealms.sparrow.util;

import net.momirealms.sparrow.nbt.CompoundTag;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public record ServerLocation(@NotNull String server, @NotNull String world, double x, double y, double z, float yaw, float pitch) {

    @NotNull
    public static ServerLocation from(@NotNull String server, @NotNull Location location) {
        return new ServerLocation(
                server,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    @NotNull
    public static ServerLocation fromNbt(@NotNull CompoundTag tag) {
        String server = tag.getString("server");
        String world = tag.getString("world");
        double x = tag.getDouble("x", Double.NaN);
        double y = tag.getDouble("y", Double.NaN);
        double z = tag.getDouble("z", Double.NaN);
        float yaw = tag.getFloat("yaw", Float.NaN);
        float pitch = tag.getFloat("pitch", Float.NaN);
        if (server == null || world == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Server location requires a server, a world and finite coordinates and rotation");
        }
        return new ServerLocation(server, world, x, y, z, yaw, pitch);
    }

    @NotNull
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("server", this.server);
        tag.putString("world", this.world);
        tag.putDouble("x", this.x);
        tag.putDouble("y", this.y);
        tag.putDouble("z", this.z);
        tag.putFloat("yaw", this.yaw);
        tag.putFloat("pitch", this.pitch);
        return tag;
    }
}
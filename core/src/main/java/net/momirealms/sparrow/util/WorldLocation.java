package net.momirealms.sparrow.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.momirealms.sparrow.nbt.CompoundTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record WorldLocation(@NotNull String world, double x, double y, double z, float yaw, float pitch) {

    @NotNull
    public static WorldLocation from(@NotNull Location location) {
        return new WorldLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    @NotNull
    public static WorldLocation fromNbt(@NotNull CompoundTag tag) {
        String world = tag.getString("world", null);
        double x = tag.getDouble("x", Double.NaN);
        double y = tag.getDouble("y", Double.NaN);
        double z = tag.getDouble("z", Double.NaN);
        float yaw = tag.getFloat("yaw", Float.NaN);
        float pitch = tag.getFloat("pitch", Float.NaN);
        if (world == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Teleport location requires a world and finite coordinates and rotation");
        }
        return new WorldLocation(world, x, y, z, yaw, pitch);
    }

    @NotNull
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("world", this.world);
        tag.putDouble("x", this.x);
        tag.putDouble("y", this.y);
        tag.putDouble("z", this.z);
        tag.putFloat("yaw", this.yaw);
        tag.putFloat("pitch", this.pitch);
        return tag;
    }

    @NotNull
    public static WorldLocation fromJson(@NotNull String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return new WorldLocation(
                object.get("world").getAsString(),
                object.get("x").getAsDouble(),
                object.get("y").getAsDouble(),
                object.get("z").getAsDouble(),
                object.get("yaw").getAsFloat(),
                object.get("pitch").getAsFloat()
        );
    }

    @NotNull
    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("world", this.world);
        // 坐标保留 3 位小数.
        json.addProperty("x", round(this.x, 1000));
        json.addProperty("y", round(this.y, 1000));
        json.addProperty("z", round(this.z, 1000));
        // 朝向保留 2 位小数.
        json.addProperty("yaw", round(this.yaw, 100));
        json.addProperty("pitch", round(this.pitch, 100));
        return GsonHelper.toString(json);
    }

    @Nullable
    public Location resolve() {
        World loaded = Bukkit.getWorld(this.world);
        if (loaded == null || !Double.isFinite(this.x) || !Double.isFinite(this.y) || !Double.isFinite(this.z) || !Float.isFinite(this.yaw) || !Float.isFinite(this.pitch)) {
            return null;
        }
        return new Location(loaded, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    private static double round(double value, int scale) {
        return Math.round(value * scale) / (double) scale;
    }
}

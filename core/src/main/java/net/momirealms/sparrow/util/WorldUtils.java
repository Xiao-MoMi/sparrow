package net.momirealms.sparrow.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WorldUtils {
    private WorldUtils() {
    }

    @Nullable
    public static World next(@NotNull World current) {
        List<World> worlds = Bukkit.getWorlds();
        int size = worlds.size();
        int start = worlds.indexOf(current);
        for (int offset = 1; offset <= size; offset++) {
            World world = worlds.get((start + offset) % size);
            if (!world.equals(current)) return world;
        }
        return null;
    }

    @NotNull
    public static Location destination(@NotNull Location source, @NotNull World target) {
        double scale = 1;
        boolean fromNether = source.getWorld().getEnvironment() == World.Environment.NETHER;
        boolean toNether = target.getEnvironment() == World.Environment.NETHER;
        if (fromNether != toNether) {
            scale = toNether ? 0.125 : 8;
        }
        return new Location(target, source.getX() * scale, source.getY(), source.getZ() * scale, source.getYaw(), source.getPitch());
    }
}

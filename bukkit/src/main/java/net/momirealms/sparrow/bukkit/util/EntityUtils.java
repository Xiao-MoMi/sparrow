package net.momirealms.sparrow.bukkit.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public final class EntityUtils {

    private EntityUtils() {}

    /**
     * Changes the world of the entity. The location of the entity will be adjusted to the new world.
     *
     * @param entity the entity to change the world of.
     * @param to the world to change to.
     */
    public static void changeWorld(@NotNull Entity entity, @NotNull World to) {
        requireNonNull(entity, "entity");
        requireNonNull(to, "to");
        var fromEnv = entity.getWorld().getEnvironment();
        var toEnv = to.getEnvironment();
        var location = entity.getLocation();
        var x = location.getX();
        var y = location.getY();
        var z = location.getZ();
        if (fromEnv != World.Environment.NETHER && toEnv == World.Environment.NETHER) {
            x /= 8;
            z /= 8;
        } else if (fromEnv == World.Environment.NETHER && toEnv != World.Environment.NETHER) {
            x *= 8;
            z *= 8;
        }
        int height = (int) Math.ceil(entity.getHeight());
        Location toLocation = new Location(to, x, y, z, location.getYaw(), location.getPitch());
        int space = 0;
        while (true) {
            Block block = toLocation.getBlock();
            if (block.isPassable() && !block.isLiquid()) {
                if (space + 1 >= height) {
                    break;
                } else {
                    space++;
                    toLocation.add(0,1,0);
                }
            } else {
                space = 0;
                toLocation.add(0,1,0);
            }
        }
        entity.teleport(toLocation.subtract(0,height - 1,0));
    }
}

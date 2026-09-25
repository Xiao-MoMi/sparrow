package net.momirealms.sparrow.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldUtilsTest {
    @Test
    void scalesNetherCoordinatesInBothDirections() {
        World normal = mock(World.class);
        World nether = mock(World.class);
        when(normal.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        Location source = new Location(normal, -80, 64, 160, 90, 15);
        Location destination = WorldUtils.destination(source, nether);
        assertEquals(-10, destination.getX());
        assertEquals(20, destination.getZ());
        assertEquals(64, destination.getY());
        assertEquals(90, destination.getYaw());
        assertEquals(15, destination.getPitch());
        assertEquals(source, WorldUtils.destination(destination, normal));
        assertEquals(source, WorldUtils.destination(source, normal));
    }
}

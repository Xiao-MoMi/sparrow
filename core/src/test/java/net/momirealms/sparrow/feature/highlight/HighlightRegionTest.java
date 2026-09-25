package net.momirealms.sparrow.feature.highlight;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HighlightRegionTest {
    @Test
    void countsBothEndpointsForSingleBlocksLinesAndPlanes() {
        assertEquals(1, HighlightRegion.between(new Location(null, 3, 4, 5), new Location(null, 3, 4, 5), 1).volume());
        assertEquals(32, HighlightRegion.between(new Location(null, 0, 4, 0), new Location(null, 31, 4, 0), 32).volume());
        assertThrows(IllegalArgumentException.class, () -> HighlightRegion.between(new Location(null, 0, 4, 0), new Location(null, 32, 4, 0), 32));
        assertThrows(IllegalArgumentException.class, () -> HighlightRegion.between(new Location(null, 0, 4, 0), new Location(null, 255, 4, 255), 32768));
    }

    @Test
    void rejectsExtremeCoordinatesWithoutOverflow() {
        assertThrows(IllegalArgumentException.class, () -> HighlightRegion.between(new Location(null, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE), new Location(null, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), Integer.MAX_VALUE));
    }

    @Test
    void normalizesReversedAndNegativeFractionalCoordinates() {
        HighlightRegion region = HighlightRegion.between(new Location(null, 1.9, 3.5, 2.1), new Location(null, -0.1, 1, -1.9), 100);
        assertEquals(new HighlightRegion(-1, 1, -2, 3, 3, 5), region);
    }

    @Test
    void outlineContainsOnlyTheOuterShell() {
        HighlightRegion region = new HighlightRegion(0, 0, 0, 3, 3, 3);
        int visible = 0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (region.visible(x, y, z, null)) {
                        visible++;
                    }
                }
            }
        }
        assertEquals(26, visible);
        assertFalse(region.visible(1, 1, 1, null));
    }

    @Test
    void solidFilterIncludesBoundaryAndCavityWallsButNotBuriedBlocks() {
        HighlightRegion region = new HighlightRegion(0, 0, 0, 5, 5, 5);
        boolean[] solid = new boolean[region.volume()];
        Arrays.fill(solid, true);
        assertTrue(region.visible(0, 2, 2, solid));
        assertFalse(region.visible(1, 2, 2, solid));
        solid[region.index(2, 2, 2)] = false;
        assertFalse(region.visible(2, 2, 2, solid));
        assertTrue(region.visible(1, 2, 2, solid));
        assertTrue(region.visible(3, 2, 2, solid));
        assertTrue(region.visible(2, 1, 2, solid));
        assertTrue(region.visible(2, 3, 2, solid));
        assertTrue(region.visible(2, 2, 1, solid));
        assertTrue(region.visible(2, 2, 3, solid));
        assertFalse(region.visible(1, 1, 1, solid));
    }
}

package net.momirealms.sparrow.feature.highlight;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

record HighlightRegion(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
    @NotNull
    static HighlightRegion between(@NotNull Location first, @NotNull Location second, int limit) {
        long x = Math.abs((long) first.getBlockX() - second.getBlockX()) + 1;
        long y = Math.abs((long) first.getBlockY() - second.getBlockY()) + 1;
        long z = Math.abs((long) first.getBlockZ() - second.getBlockZ()) + 1;
        // 逐维除法校验, 极端坐标也不会让乘法溢出后绕过上限.
        if (x > limit || y > limit / x || z > limit / (x * y)) {
            throw new IllegalArgumentException("Selection exceeds " + limit + " blocks");
        }
        return new HighlightRegion(Math.min(first.getBlockX(), second.getBlockX()), Math.min(first.getBlockY(), second.getBlockY()), Math.min(first.getBlockZ(), second.getBlockZ()), (int) x, (int) y, (int) z);
    }

    int volume() { return this.sizeX * this.sizeY * this.sizeZ; }

    int index(int x, int y, int z) { return (x * this.sizeY + y) * this.sizeZ + z; }

    boolean boundary(int x, int y, int z) {
        return x == 0 || y == 0 || z == 0 || x == this.sizeX - 1 || y == this.sizeY - 1 || z == this.sizeZ - 1;
    }

    boolean visible(int x, int y, int z, boolean[] solid) {
        if (solid == null) return this.boundary(x, y, z);
        if (!solid[this.index(x, y, z)]) return false;
        return this.boundary(x, y, z)
                || !solid[this.index(x - 1, y, z)] || !solid[this.index(x + 1, y, z)]
                || !solid[this.index(x, y - 1, z)] || !solid[this.index(x, y + 1, z)]
                || !solid[this.index(x, y, z - 1)] || !solid[this.index(x, y, z + 1)];
    }
}

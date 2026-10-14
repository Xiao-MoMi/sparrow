package net.momirealms.sparrow.teleport.search;

import net.momirealms.sparrow.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public record RandomTeleportArea(
        @NotNull String world,
        double centerX,
        double centerZ,
        double minRadius,
        double maxRadius,
        @Nullable Integer minY,
        @Nullable Integer maxY
) {

    @NotNull
    public static RandomTeleportArea fromNbt(@NotNull CompoundTag tag) {
        String world = tag.getString("world", null);
        if (world == null) throw new IllegalArgumentException("RTP area requires a world");
        RandomTeleportArea area = new RandomTeleportArea(
                world,
                tag.getDouble("center_x", Double.NaN),
                tag.getDouble("center_z", Double.NaN),
                tag.getDouble("min_radius", Double.NaN),
                tag.getDouble("max_radius", Double.NaN),
                tag.containsKey("min_y") ? tag.getInt("min_y") : null,
                tag.containsKey("max_y") ? tag.getInt("max_y") : null
        );
        area.validate();
        return area;
    }

    @NotNull
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("world", this.world);
        tag.putDouble("center_x", this.centerX);
        tag.putDouble("center_z", this.centerZ);
        tag.putDouble("min_radius", this.minRadius);
        tag.putDouble("max_radius", this.maxRadius);
        if (this.minY != null) tag.putInt("min_y", this.minY);
        if (this.maxY != null) tag.putInt("max_y", this.maxY);
        return tag;
    }

    public void validate() {
        if (!Double.isFinite(this.centerX) || !Double.isFinite(this.centerZ)) throw new IllegalArgumentException("RTP center must be finite");
        if (!Double.isFinite(this.minRadius) || !Double.isFinite(this.maxRadius) || this.minRadius < 0 || this.maxRadius <= this.minRadius) {
            throw new IllegalArgumentException("RTP radii must satisfy 0 <= min-radius < max-radius");
        }
        if (this.minY != null && this.maxY != null && this.minY > this.maxY) throw new IllegalArgumentException("RTP min-y must not exceed max-y");
    }

    boolean contains(double x, double z) {
        double radius = Math.max(Math.abs(x - this.centerX), Math.abs(z - this.centerZ));
        return radius >= this.minRadius && radius <= this.maxRadius;
    }

    Sampler sampler() {
        int minX = Math.toIntExact((long) Math.ceil(this.centerX - this.maxRadius - 0.5));
        int maxX = Math.toIntExact((long) Math.floor(this.centerX + this.maxRadius - 0.5));
        int minZ = Math.toIntExact((long) Math.ceil(this.centerZ - this.maxRadius - 0.5));
        int maxZ = Math.toIntExact((long) Math.floor(this.centerZ + this.maxRadius - 0.5));
        if (this.minRadius == 0) return new Sampler(new Rectangle[]{new Rectangle(minX, maxX, minZ, maxZ)});
        // 将方环切成四个不重叠矩形, 按方块中心的数量均匀抽样, 窄环也不需要拒绝采样
        int innerMinX = Math.toIntExact((long) Math.floor(this.centerX - this.minRadius - 0.5) + 1);
        int innerMaxX = Math.toIntExact((long) Math.ceil(this.centerX + this.minRadius - 0.5) - 1);
        int innerMinZ = Math.toIntExact((long) Math.floor(this.centerZ - this.minRadius - 0.5) + 1);
        int innerMaxZ = Math.toIntExact((long) Math.ceil(this.centerZ + this.minRadius - 0.5) - 1);
        if (innerMinX > innerMaxX || innerMinZ > innerMaxZ) return new Sampler(new Rectangle[]{new Rectangle(minX, maxX, minZ, maxZ)});
        return new Sampler(new Rectangle[]{
                new Rectangle(minX, innerMinX - 1, minZ, maxZ),
                new Rectangle(innerMaxX + 1, maxX, minZ, maxZ),
                new Rectangle(innerMinX, innerMaxX, minZ, innerMinZ - 1),
                new Rectangle(innerMinX, innerMaxX, innerMaxZ + 1, maxZ)
        });
    }

    static final class Sampler {
        private final Rectangle[] rectangles;
        private final long size;

        private Sampler(Rectangle[] rectangles) {
            this.rectangles = rectangles;
            long size = 0;
            for (Rectangle rectangle : rectangles) {
                size = Math.addExact(size, rectangle.size());
            }
            this.size = size;
        }

        long next() {
            long index = ThreadLocalRandom.current().nextLong(this.size);
            for (Rectangle rectangle : this.rectangles) {
                long count = rectangle.size();
                if (index < count) {
                    long width = (long) rectangle.maxX - rectangle.minX + 1;
                    int x = (int) (rectangle.minX + index % width);
                    int z = (int) (rectangle.minZ + index / width);
                    return (x & 0xffffffffL) | ((long) z << 32);
                }
                index -= count;
            }
            throw new AssertionError("RTP sample outside area");
        }

        boolean isEmpty() {
            return this.size == 0;
        }
    }

    private record Rectangle(int minX, int maxX, int minZ, int maxZ) {

        private long size() {
            return Math.multiplyExact(Math.max(0L, (long) this.maxX - this.minX + 1), Math.max(0L, (long) this.maxZ - this.minZ + 1));
        }
    }
}
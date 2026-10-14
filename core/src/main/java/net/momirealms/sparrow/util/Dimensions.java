package net.momirealms.sparrow.util;

import net.momirealms.sparrow.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

public record Dimensions(double width, double height, double depth, double eyeHeight) {

    @NotNull
    public static Dimensions fromNbt(@NotNull CompoundTag tag) {
        double width = tag.getDouble("width", Double.NaN);
        double height = tag.getDouble("height", Double.NaN);
        double depth = tag.getDouble("depth", Double.NaN);
        double eyeHeight = tag.getDouble("eye_height", Double.NaN);
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(depth) || !Double.isFinite(eyeHeight)
                || width <= 0 || height <= 0 || depth <= 0 || eyeHeight < 0) {
            throw new IllegalArgumentException("Teleport dimensions must be finite and positive, with a non-negative eye height");
        }
        return new Dimensions(width, height, depth, eyeHeight);
    }

    @NotNull
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("width", this.width);
        tag.putDouble("height", this.height);
        tag.putDouble("depth", this.depth);
        tag.putDouble("eye_height", this.eyeHeight);
        return tag;
    }
}
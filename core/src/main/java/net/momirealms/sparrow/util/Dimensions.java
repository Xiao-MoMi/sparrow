package net.momirealms.sparrow.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.momirealms.sparrow.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

/**
 * 保存实体碰撞箱的尺寸和相对脚底的眼高, 所有长度都以方块为单位.
 * 实例创建后不可变, 实体之后移动, 改变姿态或缩放都不会修改这里保存的数值.
 * 传送准备阶段可以将这些值发送给目标服, 用于玩家尚未抵达时的落点检查.
 *
 * @param width 碰撞箱沿 X 轴的宽度, 必须有限且大于 0
 * @param height 碰撞箱的高度, 必须有限且大于 0
 * @param depth 碰撞箱沿 Z 轴的深度, 必须有限且大于 0
 * @param eyeHeight 眼睛相对脚底的高度, 必须有限且非负
 */
public record Dimensions(double width, double height, double depth, double eyeHeight) {

    @NotNull
    public static Dimensions from(@NotNull Entity entity) {
        AABB bounds = entity.getBoundingBox();
        return new Dimensions(bounds.getXsize(), bounds.getYsize(), bounds.getZsize(), entity.getEyeHeight());
    }

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
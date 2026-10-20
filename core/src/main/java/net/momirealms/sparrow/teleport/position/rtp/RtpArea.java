package net.momirealms.sparrow.teleport.position.rtp;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.ListTag;
import net.momirealms.sparrow.nbt.NumericTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record RtpArea(@NotNull String world, @NotNull List<Rectangle> rectangles) {

    public RtpArea {
        if (rectangles.isEmpty()) throw new IllegalArgumentException("RTP area requires at least one rectangle");
        rectangles = List.copyOf(rectangles);
    }

    @NotNull
    public static Builder builder(@NotNull String world) {
        return new Builder(world);
    }

    @NotNull
    public static RtpArea fromNbt(@NotNull CompoundTag tag) {
        String world = tag.getString("world");
        if (world == null || !(tag.get("rectangles") instanceof ListTag rectangleTags)) {
            throw new IllegalArgumentException("RTP area requires a world and rectangles list");
        }
        List<Rectangle> decoded = new ArrayList<>(rectangleTags.size());
        for (int index = 0, size = rectangleTags.size(); index < size; index++) {
            if (!(rectangleTags.get(index) instanceof CompoundTag rectangle)) throw new IllegalArgumentException("RTP rectangle list must contain compounds");
            decoded.add(Rectangle.fromNbt(rectangle));
        }
        return new RtpArea(world, decoded);
    }

    @NotNull
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("world", this.world);
        ListTag rectangles = new ListTag();
        for (int index = 0, size = this.rectangles.size(); index < size; index++) {
            rectangles.add(this.rectangles.get(index).toNbt());
        }
        tag.put("rectangles", rectangles);
        return tag;
    }

    public static final class Builder {
        private final String world;                                     // 区域绑定的目标世界名
        private final List<Rectangle> rectangles = new ArrayList<>();   // 按添加顺序保存的矩形

        private Builder(@NotNull String world) {
            this.world = world;
        }

        /**
         * 追加包含两端方块列的矩形, 使用目标世界的高度范围, 可以与此前添加的矩形重叠.
         *
         * @param minX 最小方块 X
         * @param minZ 最小方块 Z
         * @param maxX 最大方块 X
         * @param maxZ 最大方块 Z
         * @return 此构建器
         * @throws IllegalArgumentException 矩形边界顺序无效时
         */
        @NotNull
        public Builder rectangle(int minX, int minZ, int maxX, int maxZ) {
            this.rectangles.add(new Rectangle(minX, minZ, maxX, maxZ));
            return this;
        }

        /**
         * 追加带独立脚底高度范围的矩形, 高度只对本次添加的矩形生效.
         * 前四个参数与默认世界高度的重载相同, 最后两个参数指定包含两端的高度范围.
         * 例如 minY 为 60.5, maxY 为 100 时, 允许脚底落在该区间内的半砖或完整方块表面.
         *
         * @param minX 最小方块 X
         * @param minZ 最小方块 Z
         * @param maxX 最大方块 X
         * @param maxZ 最大方块 Z
         * @param minY 最小脚底高度, 必须为有限数值
         * @param maxY 最大脚底高度, 必须为有限数值且不小于 minY
         * @return 此构建器
         * @throws IllegalArgumentException 矩形边界顺序无效或高度不是有限数值时
         */
        @NotNull
        public Builder rectangle(int minX, int minZ, int maxX, int maxZ, double minY, double maxY) {
            this.rectangles.add(new Rectangle(minX, minZ, maxX, maxZ, minY, maxY));
            return this;
        }

        @NotNull
        public RtpArea build() {
            return new RtpArea(this.world, this.rectangles);
        }
    }

    /**
     * 保存包含两端的方块列矩形和此矩形允许的脚底高度范围.
     * 例如 (0, 0, 1, 1) 包含四列, 对应的水平中心坐标为 0.5 或 1.5.
     * minY 和 maxY 同时为 null 时使用目标世界高度, 同时填写时限定包含两端的脚底高度.
     * 高度允许小数, 用于表达半砖等方块表面的精确落脚位置.
     *
     * @param minX 最小方块 X
     * @param minZ 最小方块 Z
     * @param maxX 最大方块 X
     * @param maxZ 最大方块 Z
     * @param minY 最小脚底高度, 与 maxY 同时为 null 时使用世界高度
     * @param maxY 最大脚底高度, 指定时必须是有限数值且不小于 minY
     */
    public record Rectangle(int minX, int minZ, int maxX, int maxZ, @Nullable Double minY, @Nullable Double maxY) {

        public Rectangle {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("RTP rectangle requires ordered X/Z bounds");
            if ((minY == null) != (maxY == null)) throw new IllegalArgumentException("RTP rectangle requires both height bounds or world height");
            if (minY != null && (!Double.isFinite(minY) || !Double.isFinite(maxY) || minY > maxY)) {
                throw new IllegalArgumentException("RTP rectangle requires finite ordered height bounds");
            }
        }

        // 创建使用目标世界高度的矩形, 高度范围在目标服执行搜索时确定.
        public Rectangle(int minX, int minZ, int maxX, int maxZ) {
            this(minX, minZ, maxX, maxZ, null, null);
        }

        @NotNull
        public static Rectangle fromNbt(@NotNull CompoundTag tag) {
            if (!(tag.get("min_x") instanceof NumericTag) || !(tag.get("min_z") instanceof NumericTag)
                    || !(tag.get("max_x") instanceof NumericTag) || !(tag.get("max_z") instanceof NumericTag)) {
                throw new IllegalArgumentException("RTP rectangle requires numeric min_x, min_z, max_x and max_z");
            }
            Double minY = tag.containsKey("min_y") ? tag.getDouble("min_y", Double.NaN) : null;
            Double maxY = tag.containsKey("max_y") ? tag.getDouble("max_y", Double.NaN) : null;
            return new Rectangle(tag.getInt("min_x"), tag.getInt("min_z"), tag.getInt("max_x"), tag.getInt("max_z"), minY, maxY);
        }

        @NotNull
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("min_x", this.minX);
            tag.putInt("min_z", this.minZ);
            tag.putInt("max_x", this.maxX);
            tag.putInt("max_z", this.maxZ);
            if (this.minY != null) {
                tag.putDouble("min_y", this.minY);
                tag.putDouble("max_y", this.maxY);
            }
            return tag;
        }
    }
}
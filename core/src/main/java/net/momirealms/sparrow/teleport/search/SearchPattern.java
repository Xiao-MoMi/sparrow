package net.momirealms.sparrow.teleport.search;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.teleport.codec.TeleportCodec;
import net.momirealms.sparrow.teleport.codec.TeleportCodecRegistry;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;

public sealed interface SearchPattern extends TeleportCodec permits SearchPattern.Exact, SearchPattern.Nearby, SearchPattern.Column {
    TeleportCodecRegistry<SearchPattern> TYPES = new TeleportCodecRegistry<>() {{
        this.register(Exact.TYPE, Exact::fromNbt);
        this.register(Nearby.TYPE, Nearby::fromNbt);
        this.register(Column.TYPE, Column::fromNbt);
    }};

    /**
     * 创建原点检查配置, 起点的精确坐标和朝向由搜索调用方提供.
     *
     * @return 参数为空的原点检查配置
     */
    @NotNull
    static Exact exact() {
        return new Exact();
    }

    /**
     * 创建附近搜索的构建器, 初始水平半径和上下距离均为 0, 顺序为 NEAR_FIRST.
     * 范围可分别调整, 半径为 0 时仍会采用原点所在方块的水平中心.
     *
     * @return 带有上述初始配置的构建器
     */
    @NotNull
    static Nearby.Builder nearby() {
        return new Nearby.Builder();
    }

    /**
     * 创建竖列搜索的构建器, 初始上下距离均为 0, 顺序为 NEAR_FIRST.
     * 搜索始终保留输入原点的精确 X/Z, 上下范围可以分别调整.
     *
     * @return 带有上述初始配置的构建器
     */
    @NotNull
    static Column.Builder column() {
        return new Column.Builder();
    }

    /**
     * 描述单个范围模式内的候选顺序.
     * NEAR_FIRST 按相对输入原点的欧式距离选择结果, 各方向使用相同权重.
     * 等距时优先较高的 Y, 然后依次选择较小的 X 和 Z; 有限预算下返回已检查到的最近合格位置.
     */
    enum SearchOrder {
        NEAR_FIRST // 优先检查靠近搜索起点的候选.
    }

    /**
     * 表达对输入目标点的精确检查, 包括原有的小数坐标和朝向.
     * 配置的参数部分是空复合标签, {@link #TYPE} 足以标识这种模式.
     */
    record Exact() implements SearchPattern {
        public static final SparrowKey TYPE = SparrowKey.sparrow("exact");

        @NotNull
        public static Exact fromNbt(@NotNull CompoundTag tag) {
            return new Exact();
        }

        @NotNull
        @Override
        public SparrowKey type() {
            return TYPE;
        }

        @NotNull
        @Override
        public CompoundTag toNbt() {
            return new CompoundTag();
        }
    }

    /**
     * 保存附近搜索的水平范围, 上下距离和检查顺序.
     * 上下距离以搜索起点的脚底高度为基准, 各方向都允许显式配置为 0.
     * X/Z 各自以原点所在方块向两边扩展水平半径, 形成方形范围, 候选取方块的水平中心.
     * 精确 Y 由碰撞面决定, 半砖等非整数站立面也会作为候选, 最终高度必须仍在上下范围内.
     *
     * @param horizontalRadius 水平搜索半径, 单位为方块, 必须非负
     * @param up 允许向上搜索的距离, 单位为方块, 必须非负
     * @param down 允许向下搜索的距离, 单位为方块, 必须非负
     * @param order 候选位置的优先检查顺序
     */
    record Nearby(int horizontalRadius, int up, int down, @NotNull SearchOrder order) implements SearchPattern {
        public static final SparrowKey TYPE = SparrowKey.sparrow("nearby");

        public Nearby {
            if (horizontalRadius < 0 || up < 0 || down < 0) {
                throw new IllegalArgumentException("Nearby search requires non-negative horizontal, upward and downward distances");
            }
        }

        @NotNull
        public static Nearby fromNbt(@NotNull CompoundTag tag) {
            return new Nearby(tag.getInt("horizontal_radius", -1), tag.getInt("up", -1), tag.getInt("down", -1), SearchOrder.valueOf(tag.getString("order")));
        }

        @NotNull
        @Override
        public SparrowKey type() {
            return TYPE;
        }

        @NotNull
        @Override
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("horizontal_radius", this.horizontalRadius);
            tag.putInt("up", this.up);
            tag.putInt("down", this.down);
            tag.putString("order", this.order.name());
            return tag;
        }

        public static final class Builder {
            private int horizontalRadius;                       // 水平半径, 单位为方块, 初始为 0
            private int up;                                     // 向上距离, 单位为方块, 初始为 0
            private int down;                                   // 向下距离, 单位为方块, 初始为 0
            private SearchOrder order = SearchOrder.NEAR_FIRST; // 初始按距离由近到远检查

            private Builder() {
            }

            @NotNull
            public Builder horizontalRadius(int horizontalRadius) {
                this.horizontalRadius = horizontalRadius;
                return this;
            }

            /**
             * 设置从起点脚底高度向上扩展的距离, 非负约束在构建时检查.
             *
             * @param up 向上距离, 单位为方块, 0 也是有效配置
             * @return 此构建器
             */
            @NotNull
            public Builder up(int up) {
                this.up = up;
                return this;
            }

            /**
             * 设置从起点脚底高度向下扩展的距离, 非负约束在构建时检查.
             *
             * @param down 向下距离, 单位为方块, 0 也是有效配置
             * @return 此构建器
             */
            @NotNull
            public Builder down(int down) {
                this.down = down;
                return this;
            }

            /**
             * 指定附近候选的优先检查顺序.
             *
             * @param order 检查顺序, 覆盖初始的 NEAR_FIRST
             * @return 此构建器
             */
            @NotNull
            public Builder order(@NotNull SearchOrder order) {
                this.order = order;
                return this;
            }

            @NotNull
            public Nearby build() {
                return new Nearby(this.horizontalRadius, this.up, this.down, this.order);
            }
        }
    }

    /**
     * 保存沿搜索起点 X/Z 检查不同高度的配置.
     * 上下距离以起点脚底高度为基准, 两个方向可以分别限制.
     * 精确脚底高度按碰撞面取得, 结果仍需位于指定的高度区间内.
     *
     * <pre>{@code
     * SearchPattern pattern = SearchPattern.column()
     *         .up(32)
     *         .down(0)
     *         .order(SearchPattern.SearchOrder.NEAR_FIRST)
     *         .build();
     * }</pre>
     *
     * @param up 向上搜索距离, 单位为方块, 必须非负
     * @param down 向下搜索距离, 单位为方块, 必须非负
     * @param order 高度候选的优先检查顺序
     */
    record Column(int up, int down, @NotNull SearchOrder order) implements SearchPattern {
        public static final SparrowKey TYPE = SparrowKey.sparrow("column");

        public Column {
            if (up < 0 || down < 0) {
                throw new IllegalArgumentException("Column search requires non-negative upward and downward distances");
            }
        }

        @NotNull
        public static Column fromNbt(@NotNull CompoundTag tag) {
            return new Column(tag.getInt("up", -1), tag.getInt("down", -1), SearchOrder.valueOf(tag.getString("order")));
        }

        @NotNull
        @Override
        public SparrowKey type() {
            return TYPE;
        }

        @NotNull
        @Override
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("up", this.up);
            tag.putInt("down", this.down);
            tag.putString("order", this.order.name());
            return tag;
        }

        public static final class Builder {
            private int up;                                     // 向上距离, 单位为方块, 初始为 0
            private int down;                                   // 向下距离, 单位为方块, 初始为 0
            private SearchOrder order = SearchOrder.NEAR_FIRST; // 初始按距离由近到远检查

            private Builder() {
            }

            /**
             * 设置从起点脚底高度向上扩展的距离, 非负约束在构建时检查.
             *
             * @param up 向上距离, 单位为方块, 0 也是有效配置
             * @return 此构建器
             */
            @NotNull
            public Builder up(int up) {
                this.up = up;
                return this;
            }

            /**
             * 设置从起点脚底高度向下扩展的距离, 非负约束在构建时检查.
             *
             * @param down 向下距离, 单位为方块, 0 也是有效配置
             * @return 此构建器
             */
            @NotNull
            public Builder down(int down) {
                this.down = down;
                return this;
            }

            /**
             * 指定竖向候选高度的优先检查顺序.
             *
             * @param order 检查顺序, 覆盖初始的 NEAR_FIRST
             * @return 此构建器
             */
            @NotNull
            public Builder order(@NotNull SearchOrder order) {
                this.order = order;
                return this;
            }

            @NotNull
            public Column build() {
                return new Column(this.up, this.down, this.order);
            }
        }
    }
}
package net.momirealms.sparrow.teleport.search;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.teleport.codec.TeleportCodec;
import net.momirealms.sparrow.teleport.codec.TeleportCodecRegistry;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * 创建附近搜索的构建器, 水平范围, 上下距离和顺序都需要显式设置.
     *
     * @return 各配置项均处于未设置状态的构建器
     */
    @NotNull
    static Nearby.Builder nearby() {
        return new Nearby.Builder();
    }

    /**
     * 创建竖列搜索的构建器, 需要显式设置上下距离和顺序.
     *
     * @return 各配置项均处于未设置状态的构建器
     */
    @NotNull
    static Column.Builder column() {
        return new Column.Builder();
    }

    /**
     * 描述单个搜索模式内部优先检查哪些候选位置.
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
            private int horizontalRadius;                           // 水平半径, 单位为方块, null 表示未设置
            private int up;                                         // 向上距离, 单位为方块, null 表示未设置
            private int down;                                       // 向下距离, 单位为方块, null 表示未设置
            private SearchOrder order = SearchOrder.NEAR_FIRST;     // 优先检查顺序, null 表示未设置

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
             * @param order 检查顺序, <strong>必须显式设置</strong>
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
            private int up;                                     // 向上距离, 单位为方块, null 表示未设置
            private int down;                                   // 向下距离, 单位为方块, null 表示未设置
            private SearchOrder order = SearchOrder.NEAR_FIRST; // 优先检查顺序, null 表示未设置

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
             * @param order 检查顺序, <strong>必须显式设置</strong>
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
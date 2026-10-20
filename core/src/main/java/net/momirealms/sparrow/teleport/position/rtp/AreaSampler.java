package net.momirealms.sparrow.teleport.position.rtp;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.momirealms.sparrow.teleport.search.SearchContext;
import net.momirealms.sparrow.util.Dimensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class AreaSampler {
    private static final Comparator<PreparedRectangle> Z_ORDER = Comparator.comparingInt(PreparedRectangle::minZ); // 按 Z 起点排序, 接着合并重叠或相邻的范围
    private static final Comparator<HeightRange> HEIGHT_ORDER = Comparator.comparingDouble(HeightRange::minY);      // 从低到高排列这一列允许的高度范围

    private final SearchBudget budget;                          // 整轮搜索共用的次数和时限
    private final List<WorldArea> areas = new ArrayList<>();    // 按配置顺序保存世界及其矩形, 已排除超出世界范围的部分
    private final List<Strip> strips = new ArrayList<>();       // 互不重叠的小矩形, 用来给可选的列编号
    private final Long2LongOpenHashMap swaps = new Long2LongOpenHashMap(); // 抽样位置 -> 实际列编号, 只保存发生过交换的位置
    private long remaining;                                   // 本轮还没作为随机起点抽过的列数

    /**
     * 根据世界边界和高度整理矩形, 去掉完全放不下玩家的矩形.
     *
     * @param areas 每个世界一项的区域配置, 来自已经校验的 RTPPos
     * @param contexts 对应的搜索环境, <strong>长度与 areas 相同, 顺序及世界逐项对应</strong>
     * @param budget 在区域准备之前创建的本次搜索预算
     */
    AreaSampler(@NotNull List<RtpArea> areas, @NotNull List<SearchContext> contexts, @NotNull SearchBudget budget) {
        this.budget = budget;
        for (int index = 0, size = areas.size(); index < size && this.budget.active(); index++) {
            WorldArea area = this.prepare(index, areas.get(index), contexts.get(index));
            this.areas.add(area);
            this.addStrips(area);
        }
    }

    // 去掉矩形超出世界边界和高度的部分, 各矩形仍使用自己的高度限制.
    @NotNull
    private WorldArea prepare(int index, @NotNull RtpArea area, @NotNull SearchContext context) {
        Dimensions dimensions = context.dimensions();
        WorldBorder border = context.chunks().getWorldBorder();
        // 玩家站在方块列中心, 坐标要加 0.5. 世界边界内还要留出半个身体宽度, 才能容纳完整身体.
        double minX = Math.max(-Level.MAX_LEVEL_SIZE, Math.ceil(border.getMinX() + dimensions.width() / 2 - 0.5));
        double maxX = Math.min(Level.MAX_LEVEL_SIZE - 1, Math.floor(border.getMaxX() - dimensions.width() / 2 - 0.5));
        double minZ = Math.max(-Level.MAX_LEVEL_SIZE, Math.ceil(border.getMinZ() + dimensions.depth() / 2 - 0.5));
        double maxZ = Math.min(Level.MAX_LEVEL_SIZE - 1, Math.floor(border.getMaxZ() - dimensions.depth() / 2 - 0.5));
        double minY = context.chunks().getMinY();
        double maxY = minY + context.chunks().getHeight() - dimensions.height();
        List<PreparedRectangle> rectangles = new ArrayList<>();
        if (minX > maxX || minZ > maxZ || minY > maxY) return new WorldArea(index, context, List.of());
        for (int rectangleIndex = 0, size = area.rectangles().size(); rectangleIndex < size && this.budget.active(); rectangleIndex++) {
            RtpArea.Rectangle rectangle = area.rectangles().get(rectangleIndex);
            int clippedMinX = Math.max(rectangle.minX(), (int) minX);
            int clippedMaxX = Math.min(rectangle.maxX(), (int) maxX);
            int clippedMinZ = Math.max(rectangle.minZ(), (int) minZ);
            int clippedMaxZ = Math.min(rectangle.maxZ(), (int) maxZ);
            double clippedMinY = rectangle.minY() == null ? minY : Math.max(rectangle.minY(), minY);
            double clippedMaxY = rectangle.maxY() == null ? maxY : Math.min(rectangle.maxY(), maxY);
            if (clippedMinX > clippedMaxX || clippedMinZ > clippedMaxZ || clippedMinY > clippedMaxY) continue;
            rectangles.add(new PreparedRectangle(clippedMinX, clippedMinZ, clippedMaxX, clippedMaxZ, new HeightRange(clippedMinY, clippedMaxY)));
        }
        return new WorldArea(index, context, List.copyOf(rectangles));
    }

    // 沿所有矩形的 X 边界切分, 再把每段内重叠或相邻的 Z 范围合起来.
    // 得到的 Strip 都是互不重叠的小矩形, 同一列只占一个抽样编号.
    private void addStrips(@NotNull WorldArea area) {
        List<PreparedRectangle> rectangles = area.rectangles();
        int[] boundaries = new int[rectangles.size() * 2];
        for (int index = 0, size = rectangles.size(); index < size; index++) {
            if (!this.budget.active()) return;
            PreparedRectangle rectangle = rectangles.get(index);
            boundaries[index * 2] = rectangle.minX();
            boundaries[index * 2 + 1] = rectangle.maxX() + 1;
        }
        Arrays.sort(boundaries);
        for (int index = 0; index < boundaries.length - 1; index++) {
            if (!this.budget.active()) return;
            int minX = boundaries[index];
            int maxX = boundaries[index + 1] - 1;
            if (minX > maxX) continue;
            List<PreparedRectangle> covering = new ArrayList<>();
            for (int rectangleIndex = 0, size = rectangles.size(); rectangleIndex < size; rectangleIndex++) {
                if (!this.budget.active()) return;
                PreparedRectangle rectangle = rectangles.get(rectangleIndex);
                if (rectangle.minX() <= minX && rectangle.maxX() >= maxX) {
                    covering.add(rectangle);
                }
            }
            if (covering.isEmpty()) continue;
            covering.sort(Z_ORDER);
            int minZ = covering.getFirst().minZ();
            int maxZ = covering.getFirst().maxZ();
            for (int coveringIndex = 1, size = covering.size(); coveringIndex < size; coveringIndex++) {
                if (!this.budget.active()) return;
                PreparedRectangle rectangle = covering.get(coveringIndex);
                if (rectangle.minZ() <= maxZ + 1) {
                    maxZ = Math.max(maxZ, rectangle.maxZ());
                } else {
                    this.addStrip(area, minX, minZ, maxX, maxZ);
                    minZ = rectangle.minZ();
                    maxZ = rectangle.maxZ();
                }
            }
            this.addStrip(area, minX, minZ, maxX, maxZ);
        }
    }

    // 按这个小矩形的列数分配一段编号, 接在之前的编号后面.
    private void addStrip(@NotNull WorldArea area, int minX, int minZ, int maxX, int maxZ) {
        long size = Math.multiplyExact((long) maxX - minX + 1, (long) maxZ - minZ + 1);
        this.remaining = Math.addExact(this.remaining, size);
        this.strips.add(new Strip(area, minX, minZ, maxX, this.remaining));
    }

    // 等概率抽出本轮还没抽过的一列, 记一次随机尝试. 列已抽完, 预算不足或搜索停止时返回 null.
    @Nullable
    Column next() {
        if (this.remaining == 0 || !this.budget.tryAttempt()) return null;
        long slot = ThreadLocalRandom.current().nextLong(this.remaining);
        long selected = this.swaps.getOrDefault(slot, slot);
        // 用末尾的列编号填上抽中的位置, 再缩小可抽范围. 例如 [0, 1, 2, 3] 抽中 1 后剩下 [0, 3, 2].
        long last = --this.remaining;
        long replacement = this.swaps.getOrDefault(last, last);
        this.swaps.remove(last);
        if (slot != last) {
            this.swaps.put(slot, replacement);
        }
        // 每个小矩形占一段连续编号, 用二分查找确定抽中的编号落在哪个小矩形里.
        int low = 0;
        int high = this.strips.size() - 1;
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (selected < this.strips.get(middle).end()) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        Strip strip = this.strips.get(low);
        long offset = selected - (low == 0 ? 0 : this.strips.get(low - 1).end());
        long width = (long) strip.maxX() - strip.minX() + 1;
        int blockX = (int) (strip.minX() + offset % width);
        int blockZ = (int) (strip.minZ() + offset / width);
        return this.column(strip.area().index(), blockX, blockZ);
    }

    /**
     * 找出覆盖指定 X/Z 的矩形, 汇总这些矩形允许的脚底高度.
     * 例如同一列分别允许 [60, 70] 和 [90, 100], 返回值会保留这两段, 中间的高度仍不允许使用.
     * 附近搜索也调用此方法, 按附近列自己的位置取得高度范围, 再由调用方限制相对起点的上下距离.
     * 此查询不消耗随机尝试次数, 也不把该列标记为已抽取.
     *
     * @param areaIndex 原区域配置中的下标, 可以从随机起点的 areaIndex 取得
     * @param blockX 待查询的方块 X
     * @param blockZ 待查询的方块 Z
     * @return 包含世界, X/Z 和允许高度的列, 高度范围从低到高排列; 列在区域外或搜索停止时为 null
     */
    @Nullable
    Column column(int areaIndex, int blockX, int blockZ) {
        if (!this.budget.active()) return null;
        WorldArea area = this.areas.get(areaIndex);
        List<HeightRange> heights = new ArrayList<>();
        for (int index = 0, size = area.rectangles().size(); index < size; index++) {
            if (!this.budget.active()) return null;
            PreparedRectangle rectangle = area.rectangles().get(index);
            if (blockX >= rectangle.minX() && blockX <= rectangle.maxX() && blockZ >= rectangle.minZ() && blockZ <= rectangle.maxZ()) {
                heights.add(rectangle.height());
            }
        }
        if (heights.isEmpty()) return null;
        heights.sort(HEIGHT_ORDER);
        List<HeightRange> merged = new ArrayList<>();
        HeightRange current = heights.getFirst();
        for (int index = 1, size = heights.size(); index < size; index++) {
            if (!this.budget.active()) return null;
            HeightRange next = heights.get(index);
            if (next.minY() <= current.maxY()) {
                current = new HeightRange(current.minY(), Math.max(current.maxY(), next.maxY()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return this.budget.active() ? new Column(areaIndex, area.context(), blockX, blockZ, List.copyOf(merged)) : null;
    }

    /**
     * 记录选中了哪个世界的哪一列, 以及可以在哪些高度寻找落脚点.
     * 实际水平落点取这列的中心, 即 blockX + 0.5, blockZ + 0.5.
     *
     * @param areaIndex 原区域配置中的下标, 附近列用它查询相同世界
     * @param context 此世界在整轮搜索中共享的环境与区块缓存
     * @param blockX 方块列 X
     * @param blockZ 方块列 Z
     * @param heights 非空且不可变的允许高度列表, 从低到高排列, 相邻两段之间的高度不允许使用
     */
    record Column(int areaIndex, @NotNull SearchContext context, int blockX, int blockZ, @NotNull List<HeightRange> heights) {
    }

    /**
     * 记录一段允许的脚底高度, 包含两端, 已去掉超出世界高度的部分.
     *
     * @param minY 最小允许脚底高度, 可以带小数
     * @param maxY 最大允许脚底高度, 大于或等于 minY
     */
    record HeightRange(double minY, double maxY) {
    }

    /**
     * 保存一个世界在本轮搜索中使用的矩形和环境.
     *
     * @param index 原区域配置中的下标
     * @param context 当前世界共享的搜索环境
     * @param rectangles 不可变的矩形列表, 已去掉超出世界范围的部分, 各矩形分别保存自己的高度
     */
    private record WorldArea(int index, @NotNull SearchContext context, @NotNull List<PreparedRectangle> rectangles) {
    }

    /**
     * 保存矩形在世界范围内可用的部分, 水平坐标和脚底高度都包含两端.
     *
     * @param minX 最小方块 X
     * @param minZ 最小方块 Z
     * @param maxX 最大方块 X
     * @param maxZ 最大方块 Z
     * @param height 此矩形有效的脚底高度区间
     */
    private record PreparedRectangle(int minX, int minZ, int maxX, int maxZ, @NotNull HeightRange height) {
    }

    /**
     * 保存切分后的小矩形, 每列占一个编号, 编号先沿 X 增长, 再沿 Z 增长.
     * 第一个小矩形从 0 开始编号, 后面的小矩形接着前一个的 end 编号.
     *
     * @param area 所属世界及各矩形允许的高度
     * @param minX 小矩形的最小方块 X
     * @param minZ 小矩形的最小方块 Z
     * @param maxX 小矩形的最大方块 X, 用于计算每行有多少列
     * @param end 下一个小矩形的起始编号, 例如本矩形占用 0 到 9 时 end 为 10
     */
    private record Strip(@NotNull WorldArea area, int minX, int minZ, int maxX, long end) {
    }
}
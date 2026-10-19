package net.momirealms.sparrow.teleport.search;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.util.Dimensions;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 执行一次有限范围的位置搜索, 按模式顺序产生候选并逐条调用所选规则.
 * 模式共享检查次数和时限, 原点检查及无法取得区块的候选单元都占用一次检查.
 * 同步完成的步骤在循环中继续, 异步步骤完成后回到工作执行器推进.
 *
 * <p>附近与竖列模式按距离展开候选单元, 再取得单元内的实际落脚高度.
 * 找到合格位置后仍检查可能更近的单元, 返回当前模式内已检查到的最近合格位置.
 * 等距时依次优先较高的 Y, 较小的 X 和较小的 Z.
 *
 * <p><strong>每个实例只能调用一次 find.</strong> 取消或超时会停止后续推进,
 * 已发起的区块加载与外部规则自行结束, 迟到结果不会覆盖搜索结果.
 * 本类以 null 表示正常结束但没有结果, unsafe 的解释由位置处理器负责.
 */
@ApiStatus.Internal
public final class SearchSession {
    private static final double COLLISION_EPSILON = 1.0E-7; // 落脚面探测使用的碰撞容差
    private static final double SUPPORT_DEPTH = 1.0E-5;     // 向下探测时的脚底薄层厚度

    private final SearchContext context;                // 本次搜索独享的共享环境
    private final WorldLocation origin;                 // 所有模式共用的输入原点
    private final List<SearchPattern> patterns;         // 已由处理器保存的不可变模式列表
    private final List<SafetyRule> rules;                // 已由处理器保存的不可变规则列表
    private final int maxChecks;                        // 所有模式合计可检查的候选单元数
    private final long timeoutNanos;                    // 本次搜索的总时限
    private final Executor executor = SparrowPlugin.instance().scheduler().async(); // 异步步骤完成后继续推进的位置
    private final CompletableFuture<@Nullable WorldLocation> completion = new CompletableFuture<>(); // 对外结果及停止标记

    private long startedAt;                            // find 开始时的单调时钟读数
    private int checks;                                // 已使用的候选检查次数
    private int patternIndex;                          // 下一个模式的下标
    private int ruleIndex;                             // 当前候选的下一条规则下标
    private @Nullable Candidates candidates;           // 当前范围模式的候选队列, Exact 不使用队列
    private @Nullable WorldLocation candidate;          // 正在接受规则检查的位置
    private volatile @Nullable WorldLocation best;      // 已通过全部规则的位置, 超时回调也可读取
    private double bestScore = Double.POSITIVE_INFINITY; // best 相对输入原点的距离平方, 仅推进线程使用

    public SearchSession(
            @NotNull SearchContext context,
            @NotNull WorldLocation origin,
            @NotNull List<SearchPattern> patterns,
            @NotNull List<SafetyRule> rules,
            int maxChecks,
            @NotNull Duration timeout
    ) {
        this.context = context;
        this.origin = origin;
        this.patterns = patterns;
        this.rules = rules;
        this.maxChecks = maxChecks;
        this.timeoutNanos = timeout.toNanos();
    }

    /**
     * 开始计时并推进搜索, 等待区块或异步规则的时间也计入时限.
     * 次数耗尽或超时时返回已经找到的合格位置, 尚未找到则返回 null.
     *
     * @return 本次搜索的结果; null 表示正常无结果, 实际异常保留为异常完成
     */
    @NotNull
    public CompletableFuture<@Nullable WorldLocation> find() {
        this.startedAt = System.nanoTime();
        SchedulerTask timeout = SparrowPlugin.instance().scheduler().asyncLater(this::finish, this.timeoutNanos, TimeUnit.NANOSECONDS);
        this.completion.whenComplete((found, error) -> timeout.cancel());
        this.advance();
        return this.completion;
    }

    /**
     * 推进候选生成与规则执行, 遇到尚未完成的 Future 时交出执行权.
     * 外部规则的同步异常在此边界转为搜索异常, 不会被当作候选拒绝.
     */
    private void advance() {
        try {
            while (!this.completion.isDone()) {
                if (System.nanoTime() - this.startedAt >= this.timeoutNanos) {
                    this.finish();
                    return;
                }
                // 当前候选按顺序执行规则, 只有全部通过后才记录为合格位置.
                if (this.candidate != null) {
                    if (this.ruleIndex == this.rules.size()) {
                        this.acceptCandidate();
                        continue;
                    }
                    CompletableFuture<Boolean> accepted = this.rules.get(this.ruleIndex++).test(this.context, this.candidate);
                    if (!accepted.isDone()) {
                        this.resume(accepted, b -> {
                            if (!b) this.candidate = null;
                        });
                        return;
                    }
                    if (!accepted.join()) {
                        this.candidate = null;
                    }
                    continue;
                }
                // 最后一次计数允许跑完该候选的全部规则, 取下一个候选前才检查次数.
                if (this.checks >= this.maxChecks) {
                    this.finish();
                    return;
                }
                if (this.candidates == null) {
                    if (this.patternIndex == this.patterns.size()) {
                        this.finish();
                        return;
                    }
                    this.beginPattern(this.patterns.get(this.patternIndex++));
                    continue;
                }
                Candidates current = this.candidates;
                Cell cell = current.next(this.bestScore);
                if (cell == null) {
                    if (this.best != null) {
                        this.finish();
                        return;
                    }
                    this.candidates = null;
                    continue;
                }
                this.checks++;
                // 范围模式需要先读取碰撞形状才能确定精确脚底高度.
                CompletableFuture<Boolean> ready = this.context.chunks().prepare(this.context.dimensions(), current.getX(cell.x()), current.getZ(cell.z()));
                if (!ready.isDone()) {
                    this.resume(ready, available -> this.selectCell(current, cell, available));
                    return;
                }
                this.selectCell(current, cell, ready.join());
            }
        } catch (Throwable error) {
            this.completion.completeExceptionally(error);
        }
    }

    // 将已开始的异步步骤接回搜索推进流程, 不阻塞等待线程.
    private <T> void resume(@NotNull CompletableFuture<T> future, @NotNull Consumer<T> action) {
        future.thenAcceptAsync(value -> {
            if (this.completion.isDone()) return;
            action.accept(value);
            this.advance();
        }, this.executor).whenComplete((ignored, error) -> {
            if (error != null) this.completion.completeExceptionally(error);
        });
    }

    /**
     * 按模式创建当前候选来源, 模式之间只共享原点, 环境和总预算.
     *
     * @param pattern 本次开始执行的模式
     */
    private void beginPattern(@NotNull SearchPattern pattern) {
        switch (pattern) {
            case SearchPattern.Exact ignored -> {
                this.checks++;
                this.candidate = this.origin;
                this.ruleIndex = 0;
            }
            case SearchPattern.Nearby nearby -> this.candidates = new Candidates(
                    this.context,
                    this.origin,
                    nearby.horizontalRadius(),
                    nearby.up(),
                    nearby.down(),
                    false,
                    nearby.order()
            );
            case SearchPattern.Column column -> this.candidates = new Candidates(
                    this.context,
                    this.origin,
                    0,
                    column.up(),
                    column.down(),
                    true,
                    column.order()
            );
        }
    }

    /**
     * 将已准备的候选单元转换为精确坐标, 只保留范围内且有机会改善结果的位置.
     *
     * @param current 当前模式的范围和坐标映射
     * @param cell 本次检查的单元
     * @param available 所需区块是否全部可用
     */
    private void selectCell(@NotNull Candidates current, @NotNull Cell cell, boolean available) {
        if (this.completion.isDone() || !available) return;
        double x = current.getX(cell.x());
        double z = current.getZ(cell.z());
        double y = this.getStandingY(x, cell.y(), z);
        if (y >= cell.y() + 1.0 || y < current.minY || y > current.maxY) return;
        WorldLocation location = new WorldLocation(this.origin.world(), x, y, z, this.origin.yaw(), this.origin.pitch());
        if (!this.isBetter(location, current.score(x, y, z))) return;
        this.candidate = location;
        this.ruleIndex = 0;
    }

    /**
     * 从候选单元顶部向下探测一格, 取得玩家脚底能落到的最高碰撞面.
     * 没有碰撞面时返回单元底部, 是否允许这个位置仍由所选规则判断.
     *
     * @param x 候选的精确 X
     * @param y 候选单元的整数 Y
     * @param z 候选的精确 Z
     * @return 此单元内的脚底高度
     */
    private double getStandingY(double x, int y, double z) {
        Dimensions dimensions = this.context.dimensions();
        double top = y + 1.0 - COLLISION_EPSILON;
        AABB feet = new AABB(
                x - dimensions.width() / 2 + COLLISION_EPSILON,
                top,
                z - dimensions.depth() / 2 + COLLISION_EPSILON,
                x + dimensions.width() / 2 - COLLISION_EPSILON,
                top + SUPPORT_DEPTH,
                z + dimensions.depth() / 2 - COLLISION_EPSILON
        );
        double movement = y - top;
        // 碰撞面可能来自半砖, 台阶或伸出相邻格的形状, 向下移动量由原版形状计算.
        for (VoxelShape shape : this.context.chunks().getBlockCollisions(null, feet.expandTowards(0, -1, 0))) {
            movement = shape.collide(Direction.Axis.Y, feet, movement);
        }
        return Math.max(y, top + movement);
    }

    // 记录已经通过全部规则的位置, Exact 可以直接完成搜索.
    private void acceptCandidate() {
        WorldLocation accepted = this.candidate;
        if (this.candidates == null) {
            this.completion.complete(accepted);
            return;
        }
        this.bestScore = this.candidates.score(accepted.x(), accepted.y(), accepted.z());
        this.best = accepted;
        this.candidate = null;
    }

    // 比较精确落脚位置, 等距时依次选择更高的 Y, 更小的 X 和更小的 Z.
    private boolean isBetter(@NotNull WorldLocation location, double score) {
        WorldLocation current = this.best;
        if (current == null || score < this.bestScore) return true;
        if (score > this.bestScore) return false;
        if (location.y() != current.y()) return location.y() > current.y();
        if (location.x() != current.x()) return location.x() < current.x();
        return location.z() < current.z();
    }

    // 用已找到的位置结束搜索, 尚未找到时以 null 正常完成.
    private void finish() {
        this.completion.complete(this.best);
    }

    private static final class Candidates {
        private static final Comparator<Cell> ORDER = Comparator.comparingDouble(Cell::score)
                .thenComparing(Comparator.comparingInt(Cell::y).reversed())
                .thenComparingInt(Cell::x)
                .thenComparingInt(Cell::z); // 同分单元按固定坐标顺序展开

        private final WorldLocation origin;           // 本模式使用的原点
        private final int minX;                       // 水平方块坐标下界, 包含边界
        private final int maxX;                       // 水平方块坐标上界, 包含边界
        private final int minZ;                       // 水平方块坐标下界, 包含边界
        private final int maxZ;                       // 水平方块坐标上界, 包含边界
        private final double minY;                    // 精确脚底高度下界
        private final double maxY;                    // 精确脚底高度上界
        private final boolean column;                // true 时保留原点的精确 X/Z
        private final PriorityQueue<Cell> queue;      // 待检查的距离下界队列
        private final LongOpenHashSet visited = new LongOpenHashSet(); // 已入队的方块单元坐标

        /**
         * 创建有限范围, 从最接近原点的单元开始逐步展开.
         *
         * @param context 提供世界实际高度的搜索环境
         * @param origin 本模式的输入原点
         * @param radius X/Z 两个方向的方块半径, Column 使用 0
         * @param up 相对原点脚底高度的向上距离
         * @param down 相对原点脚底高度的向下距离
         * @param column 是否保留原点的精确 X/Z
         * @param order 单元展开顺序
         */
        private Candidates(@NotNull SearchContext context, @NotNull WorldLocation origin, int radius, int up, int down, boolean column, @NotNull SearchPattern.SearchOrder order) {
            this.origin = origin;
            this.column = column;
            this.queue = new PriorityQueue<>(switch (order) {
                case NEAR_FIRST -> ORDER;
            });
            this.minX = Mth.ceil(Math.max(-Level.MAX_LEVEL_SIZE, Math.floor(origin.x()) - radius));
            this.maxX = Mth.floor(Math.min(Level.MAX_LEVEL_SIZE - 1, Math.floor(origin.x()) + radius));
            this.minZ = Mth.ceil(Math.max(-Level.MAX_LEVEL_SIZE, Math.floor(origin.z()) - radius));
            this.maxZ = Mth.floor(Math.min(Level.MAX_LEVEL_SIZE - 1, Math.floor(origin.z()) + radius));
            this.minY = Math.max(context.chunks().getMinY(), origin.y() - down);
            this.maxY = Math.min(Math.nextDown((double) context.chunks().getMinY() + context.chunks().getHeight()), origin.y() + up);
            if (this.minX > this.maxX || this.minZ > this.maxZ || this.minY > this.maxY) return;
            this.offer(
                    Mth.clamp(Mth.floor(origin.x()), this.minX, this.maxX),
                    Mth.floor(Mth.clamp(origin.y(), this.minY, this.maxY)),
                    Mth.clamp(Mth.floor(origin.z()), this.minZ, this.maxZ)
            );
        }

        /**
         * 取出下一个可能改善结果的单元, 并将相邻单元加入队列.
         *
         * @param bestScore 已找到位置的实际距离平方, 尚未找到时为正无穷
         * @return 下一个单元, 队列耗尽或剩余距离下界已更远时为 null
         */
        @Nullable
        private Cell next(double bestScore) {
            Cell cell = this.queue.peek();
            if (cell == null || cell.score() > bestScore) return null;
            this.queue.remove();
            this.offer(cell.x(), cell.y() + 1, cell.z());
            this.offer(cell.x(), cell.y() - 1, cell.z());
            if (!this.column) {
                this.offer(cell.x() - 1, cell.y(), cell.z());
                this.offer(cell.x() + 1, cell.y(), cell.z());
                this.offer(cell.x(), cell.y(), cell.z() - 1);
                this.offer(cell.x(), cell.y(), cell.z() + 1);
            }
            return cell;
        }

        // 将范围内尚未访问的单元加入队列, 评分使用该单元允许高度中的最小距离.
        private void offer(int x, int y, int z) {
            if (x < this.minX || x > this.maxX || z < this.minZ || z > this.maxZ || y < Mth.floor(this.minY) || y > Mth.floor(this.maxY)) return;
            if (!this.visited.add(BlockPos.asLong(x, y, z))) return;
            double nearestY = Mth.clamp(this.origin.y(), Math.max(y, this.minY), Math.min(y + 1.0, this.maxY));
            this.queue.add(new Cell(x, y, z, this.score(this.getX(x), nearestY, this.getZ(z))));
        }

        // 计算精确位置相对原点的欧式距离平方, 各方向使用相同权重.
        private double score(double x, double y, double z) {
            double dx = x - this.origin.x();
            double dy = y - this.origin.y();
            double dz = z - this.origin.z();
            return dx * dx + dy * dy + dz * dz;
        }

        // 将方块 X 转为附近模式的方块中心, 竖列模式保留原点 X.
        private double getX(int x) {
            return this.column ? this.origin.x() : x + 0.5;
        }

        // 将方块 Z 转为附近模式的方块中心, 竖列模式保留原点 Z.
        private double getZ(int z) {
            return this.column ? this.origin.z() : z + 0.5;
        }
    }

    /**
     * 保存一个待检查的方块单元及其距离下界.
     *
     * @param x 方块 X
     * @param y 方块 Y, 实际脚底可位于此高度以上一格内
     * @param z 方块 Z
     * @param score 此单元允许范围内的最小距离平方
     */
    private record Cell(int x, int y, int z, double score) {
    }
}
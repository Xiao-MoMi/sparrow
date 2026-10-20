package net.momirealms.sparrow.teleport.position.rtp;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.Level;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.search.SearchContext;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

final class RtpSearch {
    private final TeleportContext context; // 本次传送的玩家尺寸与业务数据
    private final RTPPos options;          // 可以复用的配置, 搜索状态单独保存在本实例
    private final SearchBudget budget;     // 构造时开始计时, 包含后续排队和区域准备的时间
    private final Executor executor = SparrowPlugin.instance().scheduler().async(); // 所有世界沿同一条异步流程搜索
    private final CompletableFuture<TeleportPosition.Result> completion = new CompletableFuture<>(); // 整轮结果及取消标记
    private volatile @Nullable CompletableFuture<RtpColumnSearch.Result> pending; // 当前列的结果, 整轮结束回调可以取消它

    private @Nullable AreaSampler sampler;             // 准备世界之后建立的抽样器, 随机起点不重复
    private @Nullable NearbyColumns nearby;            // 当前起点周围尚未检查的坐标, null 表示下一步随机抽点
    private @Nullable AreaSampler.Column originColumn; // 触发附近搜索的原始列, 提供世界与水平中心
    private @Nullable WorldLocation origin;            // 原候选的精确脚底高度, 整次附近搜索保持不变

    RtpSearch(@NotNull TeleportContext context, @NotNull RTPPos options) {
        this.context = context;
        this.options = options;
        this.budget = new SearchBudget(options);
    }

    /**
     * 安排整轮超时并开始准备区域, 立即返回可取消的结果.
     * 正常完成, 异常完成和取消都会清理计时任务, 停止预算并取消尚未完成的列搜索.
     *
     * @return 找到时为 Success, 世界无效或搜索无结果时为 Fail, 实际异常保留为异常完成
     */
    @NotNull
    CompletableFuture<TeleportPosition.Result> find() {
        SchedulerTask timeout = SparrowPlugin.instance().scheduler().asyncLater(this::finish, this.budget.remainingNanos(), TimeUnit.NANOSECONDS);
        this.completion.whenComplete((result, error) -> {
            this.budget.stop();
            timeout.cancel();
            CompletableFuture<RtpColumnSearch.Result> current = this.pending;
            if (current != null) {
                current.cancel(false);
            }
        });
        CompletableFuture.runAsync(this::prepare, this.executor).whenComplete((ignored, error) -> {
            if (error != null) {
                this.completion.completeExceptionally(error);
            }
        });
        return this.completion;
    }

    /**
     * 按配置顺序取得已加载的世界, 为每个世界创建可复用的搜索环境.
     * 配置中任何世界不存在时都返回无效目的地, 全部就绪后再建立抽样器.
     */
    private void prepare() {
        List<SearchContext> environments = new ArrayList<>(this.options.areas().size());
        for (int index = 0, size = this.options.areas().size(); index < size; index++) {
            if (!this.canContinue()) return;
            RtpArea area = this.options.areas().get(index);
            World world = Bukkit.getWorld(area.world());
            if (world == null) {
                this.completion.complete(new TeleportPosition.Result.Fail(MessageConstants.TELEPORT_REJECTED_INVALID));
                return;
            }
            environments.add(new SearchContext(this.context, world, this.options.chunks()));
        }
        if (!this.canContinue()) return;
        this.sampler = new AreaSampler(this.options.areas(), environments, this.budget);
        this.advance();
    }

    /**
     * 优先检查当前起点附近的列, 附近范围用完后再抽随机起点.
     * 同步完成的列在循环里接着处理, 异步完成的列通过回调继续, 连续重抽不会增加调用栈深度.
     */
    private void advance() {
        try {
            while (this.canContinue()) {
                if (!this.budget.canCheck()) {
                    this.finish();
                    return;
                }
                if (this.nearby != null) {
                    Offset offset = this.nearby.next();
                    if (offset == null) {
                        this.nearby = null;
                        this.originColumn = null;
                        this.origin = null;
                        continue;
                    }
                    AreaSampler.Column column = this.sampler.column(
                            this.originColumn.areaIndex(),
                            this.originColumn.blockX() + offset.x(),
                            this.originColumn.blockZ() + offset.z()
                    );
                    if (column == null) {
                        // 区域外的附近坐标也占一次检查, 防止狭小区域配大半径时只消耗时间.
                        if (!this.budget.tryCheck()) {
                            this.finish();
                            return;
                        }
                        continue;
                    }
                    if (this.searchColumn(column, false)) return;
                } else {
                    AreaSampler.Column column = this.sampler.next();
                    if (column == null) {
                        this.finish();
                        return;
                    }
                    if (this.searchColumn(column, true)) return;
                }
            }
        } catch (Throwable error) {
            this.completion.completeExceptionally(error);
        }
    }

    /**
     * 使用同一份扫描配置和预算检查一列, 附近列额外限制与原候选的高度距离.
     * 列的 Future 在整轮结束时会被取消, 列创建期间已经结束的情况也会在登记后处理.
     *
     * @param column 本次检查的列, 高度已经由对应矩形计算
     * @param random 是否为随机起点, 只有随机起点的拒绝结果会展开附近搜索
     * @return true 表示正在等待异步结果或整轮已结束, 当前推进过程应当返回
     */
    private boolean searchColumn(@NotNull AreaSampler.Column column, boolean random) {
        RtpColumnSearch search = random
                ? new RtpColumnSearch(column, this.options, this.budget)
                : new RtpColumnSearch(column, this.options, this.budget, this.origin.y() - this.options.nearbyDown(), this.origin.y() + this.options.nearbyUp());
        CompletableFuture<RtpColumnSearch.Result> searching = search.find();
        this.pending = searching;
        if (!this.canContinue()) {
            searching.cancel(false);
            return true;
        }
        if (searching.isDone()) {
            this.pending = null;
            this.apply(column, random, searching.join());
            return false;
        }
        searching.thenAcceptAsync(result -> {
            this.pending = null;
            if (!this.canContinue()) return;
            this.apply(column, random, result);
            this.advance();
        }, this.executor).whenComplete((ignored, error) -> {
            if (error != null && this.canContinue()) {
                this.completion.completeExceptionally(error);
            }
        });
        return true;
    }

    /**
     * 采用合格落点, 或为被规则拒绝的随机候选建立一次附近搜索.
     * 附近列的拒绝结果只让流程继续检查下一列, 原候选的坐标和高度保持不变.
     *
     * @param column 刚完成检查的列
     * @param random 是否为随机起点
     * @param result 列扫描与规则检查的结果
     */
    private void apply(@NotNull AreaSampler.Column column, boolean random, @NotNull RtpColumnSearch.Result result) {
        if (!this.canContinue()) return;
        switch (result) {
            case RtpColumnSearch.Result.Found found -> this.completion.complete(new TeleportPosition.Result.Success(found.location()));
            case RtpColumnSearch.Result.Rejected rejected -> {
                if (random && this.options.nearbyRadius() > 0) {
                    this.originColumn = column;
                    this.origin = rejected.location();
                    this.nearby = new NearbyColumns(this.options.nearbyRadius(), column.blockX(), column.blockZ());
                }
            }
            case RtpColumnSearch.Result.Miss ignored -> {
            }
        }
    }

    /**
     * 在推进和接收异步结果之前检查结束状态与整轮时限.
     * 当前列已经通过全部规则时仍可提交最后一次检查的结果, 下一列的次数限制由 advance 检查.
     *
     * @return 整轮尚未完成且仍在时限内时为 true
     */
    private boolean canContinue() {
        if (this.completion.isDone()) return false;
        if (this.budget.active()) return true;
        this.finish();
        return false;
    }

    /**
     * 用搜索无结果结束整轮 RTP, 用于时限, 次数或可抽列耗尽.
     */
    private void finish() {
        this.completion.complete(new TeleportPosition.Result.Fail(MessageConstants.TELEPORT_REJECTED_RTP));
    }

    /**
     * 逐步取出起点附近的方块列, 按水平距离从近到远排列, 同距离先取较小的 X, 再取较小的 Z.
     * X 和 Z 的偏移分别限制在 radius 内, 起点自身已经检查过, 不加入队列.
     * 区域外的坐标仍可通向更远处的另一个矩形, 是否属于区域由调用方逐列查询.
     */
    private static final class NearbyColumns {
        private static final Comparator<Offset> ORDER = Comparator
                .comparingLong((Offset offset) -> (long) offset.x() * offset.x() + (long) offset.z() * offset.z())
                .thenComparingInt(Offset::x)
                .thenComparingInt(Offset::z); // 距离相同也保持固定检查顺序

        private final int minX; // 最小 X 偏移, 已限制在世界可表示坐标内
        private final int maxX; // 最大 X 偏移, 已限制在世界可表示坐标内
        private final int minZ; // 最小 Z 偏移, 已限制在世界可表示坐标内
        private final int maxZ; // 最大 Z 偏移, 已限制在世界可表示坐标内
        private final PriorityQueue<Offset> queue = new PriorityQueue<>(ORDER); // 尚未检查的相邻坐标
        private final LongOpenHashSet visited = new LongOpenHashSet();          // 已入队的偏移, 防止从不同方向重复加入

        /**
         * 从起点的四个相邻列开始, 后续每取出一列再加入它的相邻列.
         * 先限制到世界可表示范围, 后续偏移和实际方块坐标都可以用 int 计算.
         *
         * @param radius 正的水平半径, X/Z 两个方向分别受此限制
         * @param originX 原候选的方块 X
         * @param originZ 原候选的方块 Z
         */
        private NearbyColumns(int radius, int originX, int originZ) {
            this.minX = (int) Math.max(-(long) radius, -(long) Level.MAX_LEVEL_SIZE - originX);
            this.maxX = (int) Math.min(radius, (long) Level.MAX_LEVEL_SIZE - 1 - originX);
            this.minZ = (int) Math.max(-(long) radius, -(long) Level.MAX_LEVEL_SIZE - originZ);
            this.maxZ = (int) Math.min(radius, (long) Level.MAX_LEVEL_SIZE - 1 - originZ);
            this.visited.add(0L);
            this.expand(0, 0);
        }

        /**
         * 取出最近的尚未检查坐标, 并将它周围的坐标加入队列.
         *
         * @return 相对原候选的偏移, 附近范围全部检查完时为 null
         */
        @Nullable
        private Offset next() {
            Offset offset = this.queue.poll();
            if (offset == null) return null;
            this.expand(offset.x(), offset.z());
            return offset;
        }

        /**
         * 把一个坐标四周的列交给范围和去重检查.
         *
         * @param x 当前列的 X 偏移
         * @param z 当前列的 Z 偏移
         */
        private void expand(int x, int z) {
            this.offer(x - 1, z);
            this.offer(x + 1, z);
            this.offer(x, z - 1);
            this.offer(x, z + 1);
        }

        /**
         * 将范围内尚未入队的列加入待检查队列, 一个偏移在本次附近搜索中只加入一次.
         *
         * @param x 待加入的 X 偏移
         * @param z 待加入的 Z 偏移
         */
        private void offer(int x, int z) {
            if (x < this.minX || x > this.maxX || z < this.minZ || z > this.maxZ) return;
            if (this.visited.add((x & 0xffffffffL) | ((long) z << 32))) {
                this.queue.add(new Offset(x, z));
            }
        }
    }

    /**
     * 保存附近一列相对原候选的水平偏移.
     *
     * @param x 方块 X 偏移
     * @param z 方块 Z 偏移
     */
    private record Offset(int x, int z) {
    }
}
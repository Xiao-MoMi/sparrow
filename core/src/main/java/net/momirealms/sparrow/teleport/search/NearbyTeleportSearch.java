package net.momirealms.sparrow.teleport.search;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportDimensions;
import net.momirealms.sparrow.teleport.processor.CandidateChecks;
import net.momirealms.sparrow.teleport.processor.CandidateContext;
import net.momirealms.sparrow.teleport.processor.TeleportProcessor;
import net.momirealms.sparrow.util.TeleportSafety;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class NearbyTeleportSearch {
    private static final CompletableFuture<Void> EVALUATED = CompletableFuture.completedFuture(null);

    private final TeleportDimensions dimensions;
    private final SearchChunks chunks;
    private final Executor executor;
    private final int maxY; // 检测体积顶部允许达到的高度上界
    private final int maxChecks;
    private final CandidateChecks rules;
    private final CompletableFuture<WorldLocation> completion = new CompletableFuture<>(); // 本次搜索的结果和取消状态
    private int checks;

    /**
     * 创建使用指定玩家检测体积和候选规则的落点搜索器.
     *
     * @param maxChecks 本次搜索可检查的候选总数
     * @param rules 按顺序检查的候选规则, 搜索期间<strong>不得修改列表</strong>
     */
    public NearbyTeleportSearch(
            @NotNull ServerLevel world,
            @NotNull SparrowPlayer player,
            @NotNull TeleportDimensions dimensions,
            boolean allowNetherRoof,
            int maxChecks,
            @NotNull List<TeleportProcessor.CandidateRule> rules
    ) {
        this.dimensions = dimensions;
        this.chunks = new SearchChunks(world, false);
        // Paper 和 Folia 在工作线程扫描, Spigot 在主线程扫描
        this.executor = VersionHelper.hasPaperPatch ? SparrowPlugin.instance().scheduler().async() : SparrowPlugin.instance().scheduler().platform();
        this.maxY = TeleportSafety.getMaxY(world, allowNetherRoof);
        this.maxChecks = maxChecks;
        this.rules = new CandidateChecks(new CandidateContext(player, dimensions, this.chunks, this.maxY), rules, this.completion);
    }

    /**
     * 检查原点, 失败后依次搜索附近区域和原点所在列的可用高度.
     * <p>每个 NearbyTeleportSearch 实例<strong>只能调用一次</strong>.
     *
     * @return 搜索结果的 Future, 找不到落点时以 null 完成. 取消后停止后续扫描, 已提交的区块加载自行结束
     */
    @NotNull
    public CompletableFuture<@Nullable WorldLocation> find(@NotNull WorldLocation origin, int horizontalRadius, int verticalRadius) {
        CompletableFuture<WorldLocation> searching = CompletableFuture.completedFuture(origin)
                .thenComposeAsync(this::checkLocation, this.executor)
                .thenCompose(found -> {
                    if (found != null) return CompletableFuture.completedFuture(found);
                    return this.nearby(origin, horizontalRadius, verticalRadius);
                })
                .thenCompose(found -> {
                    if (found != null) return CompletableFuture.completedFuture(found);
                    return this.vertical(origin);
                });

        searching.whenComplete((found, error) -> {
            if (error != null) {
                this.completion.completeExceptionally(error);
            } else {
                this.completion.complete(found);
            }
        });
        return this.completion;
    }

    // 按输入坐标原样检测, 原点也占用一次检查预算
    private CompletableFuture<WorldLocation> checkLocation(WorldLocation location) {
        if (this.completion.isDone()) return CompletableFuture.failedFuture(new CancellationException());
        if (this.isExhausted()) return CompletableFuture.completedFuture(null);
        this.checks++;
        if (!this.inside(location.x(), location.y(), location.z())) return CompletableFuture.completedFuture(null);
        return this.chunks.prepare(this.dimensions, location.x(), location.z())
                .thenComposeAsync(available -> {
                    if (this.completion.isDone()) return CompletableFuture.failedFuture(new CancellationException());
                    if (!available || this.isAirAroundFeet(location.x(), Mth.floor(location.y()), location.z())) return CompletableFuture.completedFuture(null);
                    CompletableFuture<Component> checked = this.rules.check(location);
                    if (checked.isDone()) return CompletableFuture.completedFuture(checked.getNow(null) == null ? location : null);
                    return checked.thenApplyAsync(reason -> reason == null ? location : null, this.executor);
                }, this.executor);
    }

    // 附近候选取方块中心, 高度范围按玩家脚底坐标计算
    private CompletableFuture<WorldLocation> nearby(WorldLocation origin, int horizontalRadius, int verticalRadius) {
        WorldBorder border = this.chunks.getWorldBorder();
        double halfWidth = this.dimensions.width() / 2;
        double halfDepth = this.dimensions.depth() / 2;
        // 候选中心留出检测体积所需的空间, 使整个体积位于世界边界内
        int minX = Mth.ceil(Math.max((double) Mth.floor(origin.x()) - horizontalRadius, border.getMinX() + halfWidth - 0.5));
        int maxX = Mth.floor(Math.min((double) Mth.floor(origin.x()) + horizontalRadius, border.getMaxX() - halfWidth - 0.5));
        int minZ = Mth.ceil(Math.max((double) Mth.floor(origin.z()) - horizontalRadius, border.getMinZ() + halfDepth - 0.5));
        int maxZ = Mth.floor(Math.min((double) Mth.floor(origin.z()) + horizontalRadius, border.getMaxZ() - halfDepth - 0.5));
        double minY = Math.max(this.chunks.getMinY(), origin.y() - verticalRadius);
        double maxY = Math.min(this.maxY - this.dimensions.height(), origin.y() + verticalRadius);
        return CompletableFuture.completedFuture(new Candidates(origin, minX, maxX, minZ, maxZ, minY, maxY, false))
                .thenComposeAsync(this::scan, this.executor);
    }

    // 保留原点的 X/Z 精确坐标, 沿整列可用高度搜索
    private CompletableFuture<WorldLocation> vertical(WorldLocation origin) {
        if (!this.insideHorizontal(origin.x(), origin.z())) return CompletableFuture.completedFuture(null);
        int x = Mth.floor(origin.x());
        int z = Mth.floor(origin.z());
        return CompletableFuture.completedFuture(new Candidates(origin, x, x, z, z, this.chunks.getMinY(), this.maxY - this.dimensions.height(), true))
                .thenComposeAsync(this::scan, this.executor);
    }

    // 按评分检查候选, 等待区块后沿用当前队列和剩余预算
    private CompletableFuture<WorldLocation> scan(Candidates candidates) {
        while (!this.completion.isDone() && !this.isExhausted() && !candidates.queue.isEmpty()) {
            Candidate next = candidates.queue.poll();
            // 队首评分是剩余候选的下界, 同分候选仍需比较实际落脚高度
            if (candidates.best != null && next.score() > candidates.bestScore) break;
            candidates.expand(next);
            // 快速排除和区块不可用的候选同样计入预算
            this.checks++;
            double x = candidates.x(next.x());
            double z = candidates.z(next.z());
            CompletableFuture<Boolean> ready = this.chunks.prepare(this.dimensions, x, z);
            if (!ready.isDone()) {
                // 区块完成回调可能来自加载线程, 继续扫描前回到选定的执行器
                return ready.thenComposeAsync(available -> {
                    if (this.completion.isDone()) return CompletableFuture.failedFuture(new CancellationException());
                    if (!available) return this.scan(candidates);
                    return this.evaluate(candidates, next, x, z)
                            .thenCompose(ignored -> this.scan(candidates));
                }, this.executor);
            }
            if (!ready.getNow(false)) continue;
            CompletableFuture<Void> evaluated = this.evaluate(candidates, next, x, z);
            if (!evaluated.isDone()) {
                return evaluated.thenComposeAsync(ignored -> this.scan(candidates), this.executor);
            }
            evaluated.getNow(null);
        }
        return CompletableFuture.completedFuture(candidates.best);
    }

    private boolean isAirAroundFeet(double x, int y, double z) {
        int minX = Mth.floor(x - this.dimensions.width() / 2 - 1.0E-7) - 1;
        int maxX = Mth.floor(x + this.dimensions.width() / 2 + 1.0E-7) + 1;
        int minZ = Mth.floor(z - this.dimensions.depth() / 2 - 1.0E-7) - 1;
        int maxZ = Mth.floor(z + this.dimensions.depth() / 2 + 1.0E-7) + 1;
        // 覆盖站立面探测和脚底支撑查询的方块范围, 包含相邻方块伸出的碰撞形状
        return this.chunks.isAir(minX, y - 2, minZ, maxX, y + 2, maxZ);
    }

    // 候选通过全部规则后才更新最佳落点, 评分相同时保留较高的位置
    private CompletableFuture<Void> evaluate(Candidates candidates, Candidate candidate, double x, double z) {
        if (this.isAirAroundFeet(x, candidate.y(), z)) return EVALUATED;
        // 站立面可位于方块内部, 评分和安全检测使用碰撞面对应的脚底高度
        double y = TeleportSafety.getStandingY(this.chunks, this.dimensions, x, candidate.y(), z);
        if (y >= candidate.y() + 1.0 || y < candidates.minY || y > candidates.maxY) return EVALUATED;
        double score = candidates.score(x, y, z);
        if (score > candidates.bestScore) return EVALUATED;
        if (score == candidates.bestScore && y <= candidates.best.y()) return EVALUATED;
        WorldLocation location = new WorldLocation(candidates.origin.world(), x, y, z, candidates.origin.yaw(), candidates.origin.pitch());
        CompletableFuture<Component> checked = this.rules.check(location);
        if (checked.isDone()) {
            if (checked.getNow(null) == null) {
                candidates.best = location;
                candidates.bestScore = score;
            }
            return EVALUATED;
        }
        return checked.thenAcceptAsync(reason -> {
            if (reason == null) {
                candidates.best = location;
                candidates.bestScore = score;
            }
        }, this.executor);
    }

    private boolean inside(double x, double y, double z) {
        return y >= this.chunks.getMinY() && y + this.dimensions.height() <= this.maxY && this.insideHorizontal(x, z);
    }

    private boolean insideHorizontal(double x, double z) {
        WorldBorder border = this.chunks.getWorldBorder();
        double halfWidth = this.dimensions.width() / 2;
        double halfDepth = this.dimensions.depth() / 2;
        return x - halfWidth >= border.getMinX() && x + halfWidth <= border.getMaxX()
                && z - halfDepth >= border.getMinZ() && z + halfDepth <= border.getMaxZ();
    }

    private boolean isExhausted() {
        return this.checks >= this.maxChecks;
    }

    private static final class Candidates {
        // 下界评分相同时先看高处, X/Z 固定其余同分候选的遍历顺序
        private static final Comparator<Candidate> ORDER = Comparator.comparingDouble(Candidate::score)
                .thenComparing(Comparator.comparingInt(Candidate::y).reversed())
                .thenComparingInt(Candidate::x)
                .thenComparingInt(Candidate::z);

        private final WorldLocation origin;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final double minY;
        private final double maxY;
        private final boolean vertical;
        private final PriorityQueue<Candidate> queue = new PriorityQueue<>(ORDER);
        private final LongOpenHashSet visited = new LongOpenHashSet();
        private WorldLocation best;
        private double bestScore = Double.POSITIVE_INFINITY;

        private Candidates(WorldLocation origin, int minX, int maxX, int minZ, int maxZ, double minY, double maxY, boolean vertical) {
            this.origin = origin;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.minY = minY;
            this.maxY = maxY;
            this.vertical = vertical;
            if (minX > maxX || minZ > maxZ || minY > maxY) return;
            this.offer(Mth.clamp(Mth.floor(origin.x()), minX, maxX), Mth.floor(Mth.clamp(origin.y(), minY, maxY)), Mth.clamp(Mth.floor(origin.z()), minZ, maxZ));
        }

        private void expand(Candidate candidate) {
            this.offer(candidate.x(), candidate.y() + 1, candidate.z());
            this.offer(candidate.x(), candidate.y() - 1, candidate.z());
            if (!this.vertical) {
                this.offer(candidate.x() - 1, candidate.y(), candidate.z());
                this.offer(candidate.x() + 1, candidate.y(), candidate.z());
                this.offer(candidate.x(), candidate.y(), candidate.z() - 1);
                this.offer(candidate.x(), candidate.y(), candidate.z() + 1);
            }
        }

        private void offer(int x, int y, int z) {
            if (x < this.minX || x > this.maxX || z < this.minZ || z > this.maxZ || y < Mth.floor(this.minY) || y > Mth.floor(this.maxY)) return;
            if (!this.visited.add(BlockPos.asLong(x, y, z))) return;
            // 落脚面可能位于候选格内部, 使用格内最低评分作为展开顺序和提前结束搜索的下界
            double nearestY = Mth.clamp(this.origin.y(), Math.max(y, this.minY), Math.min(y + 1.0, this.maxY));
            this.queue.add(new Candidate(x, y, z, this.score(this.x(x), nearestY, this.z(z))));
        }

        private double score(double x, double y, double z) {
            double dx = x - this.origin.x();
            double dy = y - this.origin.y();
            double dz = z - this.origin.z();
            // 下降的垂直距离平方按两倍权重计分, 降低地下落点的优先级
            return dx * dx + dz * dz + dy * dy * (dy < 0 ? 2 : 1);
        }

        private double x(int x) {
            return this.vertical ? this.origin.x() : x + 0.5;
        }

        private double z(int z) {
            return this.vertical ? this.origin.z() : z + 0.5;
        }
    }

    private record Candidate(int x, int y, int z, double score) {
    }
}
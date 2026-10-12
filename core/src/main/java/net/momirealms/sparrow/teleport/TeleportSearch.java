package net.momirealms.sparrow.teleport;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.TeleportSafety;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class TeleportSearch {
    private final TeleportDimensions dimensions;
    private final SearchChunks chunks;
    private final Executor executor;
    private final int maxY;
    private final int maxChecks;
    private int checks;

    // 同一实例的查询按顺序调用, 共用检查预算和区块缓存
    public TeleportSearch(@NotNull ServerLevel world, @NotNull TeleportDimensions dimensions, boolean allowNetherRoof, int maxChecks) {
        this.dimensions = dimensions;
        this.chunks = new SearchChunks(world, false);
        this.executor = VersionHelper.hasPaperPatch ? SparrowPlugin.instance().scheduler().async() : SparrowPlugin.instance().scheduler().platform();
        this.maxY = TeleportSafety.getMaxY(world, allowNetherRoof);
        this.maxChecks = maxChecks;
    }

    @NotNull
    public CompletableFuture<@Nullable WorldLocation> find(@NotNull WorldLocation origin, int horizontalRadius, int verticalRadius) {
        return this.check(origin)
                .thenCompose(found -> found != null ? CompletableFuture.completedFuture(found) : this.nearby(origin, horizontalRadius, verticalRadius))
                .thenCompose(found -> found != null ? CompletableFuture.completedFuture(found) : this.vertical(origin));
    }

    @NotNull
    public CompletableFuture<@Nullable WorldLocation> check(@NotNull WorldLocation location) {
        if (this.isExhausted()) return CompletableFuture.completedFuture(null);
        this.checks++;
        if (!this.inside(location.x(), location.y(), location.z())) return CompletableFuture.completedFuture(null);
        return this.prepare(location.x(), location.z()).thenApplyAsync(
                available -> available && TeleportSafety.isSafe(this.chunks, this.dimensions, location.x(), location.y(), location.z(), this.maxY) ? location : null,
                this.executor
        );
    }

    @NotNull
    public CompletableFuture<@Nullable WorldLocation> nearby(@NotNull WorldLocation origin, int horizontalRadius, int verticalRadius) {
        WorldBorder border = this.chunks.getWorldBorder();
        double halfWidth = this.dimensions.width() / 2;
        double halfDepth = this.dimensions.depth() / 2;
        int minX = Mth.ceil(Math.max((double) Mth.floor(origin.x()) - horizontalRadius, border.getMinX() + halfWidth - 0.5));
        int maxX = Mth.floor(Math.min((double) Mth.floor(origin.x()) + horizontalRadius, border.getMaxX() - halfWidth - 0.5));
        int minZ = Mth.ceil(Math.max((double) Mth.floor(origin.z()) - horizontalRadius, border.getMinZ() + halfDepth - 0.5));
        int maxZ = Mth.floor(Math.min((double) Mth.floor(origin.z()) + horizontalRadius, border.getMaxZ() - halfDepth - 0.5));
        double minY = Math.max(this.chunks.getMinY(), origin.y() - verticalRadius);
        double maxY = Math.min(this.maxY - this.dimensions.height(), origin.y() + verticalRadius);
        return CompletableFuture.completedFuture(new Candidates(origin, minX, maxX, minZ, maxZ, minY, maxY, false))
                .thenComposeAsync(this::scan, this.executor);
    }

    @NotNull
    public CompletableFuture<@Nullable WorldLocation> vertical(@NotNull WorldLocation origin) {
        if (!this.insideHorizontal(origin.x(), origin.z())) return CompletableFuture.completedFuture(null);
        int x = Mth.floor(origin.x());
        int z = Mth.floor(origin.z());
        return CompletableFuture.completedFuture(new Candidates(origin, x, x, z, z, this.chunks.getMinY(), this.maxY - this.dimensions.height(), true))
                .thenComposeAsync(this::scan, this.executor);
    }

    // 已完成加载的候选在循环里处理, 只有等待新区块时才接续异步阶段
    private CompletableFuture<WorldLocation> scan(Candidates candidates) {
        while (!this.isExhausted() && !candidates.queue.isEmpty()) {
            Candidate next = candidates.queue.poll();
            if (candidates.best != null && next.score() > candidates.bestScore) break;
            candidates.expand(next);
            this.checks++;
            double x = candidates.x(next.x());
            double z = candidates.z(next.z());
            CompletableFuture<Boolean> ready = this.prepare(x, z);
            if (!ready.isDone()) {
                // 区块加载可以在区域线程完成, 后续碰撞判定和扫描继续使用搜索执行器
                return ready.thenComposeAsync(available -> {
                    if (available) this.evaluate(candidates, next, x, z);
                    return this.scan(candidates);
                }, this.executor);
            }
            if (ready.join()) {
                this.evaluate(candidates, next, x, z);
            }
        }
        return CompletableFuture.completedFuture(candidates.best);
    }

    private CompletableFuture<Boolean> prepare(double x, double z) {
        int minX = Mth.floor(x - this.dimensions.width() / 2 - 1.0E-7) - 1;
        int maxX = Mth.floor(x + this.dimensions.width() / 2 + 1.0E-7) + 1;
        int minZ = Mth.floor(z - this.dimensions.depth() / 2 - 1.0E-7) - 1;
        int maxZ = Mth.floor(z + this.dimensions.depth() / 2 + 1.0E-7) + 1;
        return this.chunks.prepare(minX, minZ, maxX, maxZ);
    }

    private void evaluate(Candidates candidates, Candidate candidate, double x, double z) {
        double y = TeleportSafety.getStandingY(this.chunks, this.dimensions, x, candidate.y(), z);
        if (y >= candidate.y() + 1.0 || y < candidates.minY || y > candidates.maxY) return;
        double score = candidates.score(x, y, z);
        if (score > candidates.bestScore) return;
        if (score == candidates.bestScore && y <= candidates.best.y()) return;
        if (!TeleportSafety.isSafe(this.chunks, this.dimensions, x, y, z, this.maxY)) return;
        candidates.best = new WorldLocation(candidates.origin.world(), x, y, z, candidates.origin.yaw(), candidates.origin.pitch());
        candidates.bestScore = score;
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

    public int checks() {
        return this.checks;
    }

    public boolean isExhausted() {
        return this.checks >= this.maxChecks;
    }

    private static final class Candidates {
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
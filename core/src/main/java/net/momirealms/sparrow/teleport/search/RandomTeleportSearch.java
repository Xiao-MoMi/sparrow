package net.momirealms.sparrow.teleport.search;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportDimensions;
import net.momirealms.sparrow.teleport.processor.target.SafeLocationRule;
import net.momirealms.sparrow.teleport.processor.TeleportProcessor;
import net.momirealms.sparrow.util.TeleportSafety;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public final class RandomTeleportSearch {
    private static final int TIMEOUT_SECONDS = 9;
    private static final CompletableFuture<WorldLocation> NOT_FOUND = CompletableFuture.completedFuture(null);
    private static final CompletableFuture<ColumnResult> MISS = CompletableFuture.completedFuture(ColumnResult.MISS);
    private static final CompletableFuture<ColumnResult> SKIP = CompletableFuture.completedFuture(ColumnResult.SKIP);
    private static final CompletableFuture<ColumnResult> BLOCKED = CompletableFuture.completedFuture(ColumnResult.BLOCKED);

    private final TeleportDimensions dimensions;
    private final TeleportSearchOptions.Random options;
    private final SearchChunks chunks;
    private final CollisionGetter leaflessBlocks;
    private final boolean ceiling;
    private final int maxY;
    private final Executor executor = SparrowPlugin.instance().scheduler().async();
    private final CompletableFuture<WorldLocation> completion = new CompletableFuture<>(); // 每个实例只执行一次搜索
    private final CandidateChecks rules;
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
    private final LongOpenHashSet attempted = new LongOpenHashSet();
    private RandomTeleportArea area;
    private RandomTeleportArea.Sampler sampler;
    private double minFeetY;
    private double maxFeetY;
    private int attempts;
    private int checks;

    public RandomTeleportSearch(
            @NotNull ServerLevel world,
            @NotNull SparrowPlayer player,
            @NotNull TeleportDimensions dimensions,
            @NotNull TeleportSearchOptions.Random options,
            @NotNull List<TeleportProcessor.CandidateRule> rules
    ) {
        this.dimensions = dimensions;
        this.options = options;
        this.chunks = new SearchChunks(world, true);
        this.leaflessBlocks = new LeafIgnoringCollisionGetter(this.chunks);
        this.ceiling = world.dimensionType().hasCeiling() && !options.allowNetherRoof();
        this.maxY = TeleportSafety.getMaxY(world, options.allowNetherRoof());
        this.rules = new CandidateChecks(new CandidateContext(player, dimensions, this.chunks, this.maxY), rules, this.completion);
    }

    @NotNull
    public CompletableFuture<@Nullable WorldLocation> find(@NotNull RandomTeleportArea area) {
        if (!VersionHelper.hasPaperPatch) return CompletableFuture.failedFuture(new UnsupportedOperationException("RTP requires Paper or Folia"));
        this.completion.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        CompletableFuture<WorldLocation> searching = CompletableFuture.completedFuture(area)
                .thenComposeAsync(input -> {
                    input.validate();
                    this.area = input;
                    this.sampler = input.sampler();
                    this.minFeetY = Math.max(this.chunks.getMinY(), input.minY() == null ? this.chunks.getMinY() : input.minY());
                    this.maxFeetY = Math.min(this.maxY - this.dimensions.height(), input.maxY() == null ? this.maxY : input.maxY());
                    if (this.sampler.isEmpty() || this.minFeetY > this.maxFeetY) return NOT_FOUND;
                    return this.attempt();
                }, this.executor);
        searching.whenComplete((found, error) -> {
            if (error != null) {
                this.completion.completeExceptionally(error);
            } else {
                this.completion.complete(found);
            }
        });
        return this.completion;
    }

    private CompletableFuture<WorldLocation> attempt() {
        while (this.canCheck() && this.attempts < this.options.maxAttempts()) {
            this.attempts++;
            long point = this.sampler.next();
            int x = ChunkPos.getX(point);
            int z = ChunkPos.getZ(point);
            if (!this.attempted.add(point) || !this.inside(x, z)) {
                this.checks++;
                continue;
            }
            Column column = new Column(x, z, this.minFeetY, this.maxFeetY, !this.ceiling);
            // 列扫描正常完成时仍在工作线程, 就地衔接附近搜索
            CompletableFuture<WorldLocation> found = this.searchColumn(column)
                    .thenCompose(result -> {
                        if (result == ColumnResult.FOUND) return CompletableFuture.completedFuture(column.location);
                        if (result == ColumnResult.BLOCKED) return this.nearby(column, new NearbyColumns(this.options.horizontalRadius()));
                        return NOT_FOUND;
                    });
            if (!found.isDone()) {
                return found.thenComposeAsync(location -> location == null ? this.attempt() : CompletableFuture.completedFuture(location), this.executor);
            }
            if (found.getNow(null) != null) return found;
        }
        return NOT_FOUND;
    }

    private CompletableFuture<WorldLocation> nearby(Column origin, NearbyColumns columns) {
        while (this.canCheck() && !columns.queue.isEmpty()) {
            Offset offset = columns.next();
            int x = origin.x + offset.x;
            int z = origin.z + offset.z;
            if (!this.inside(x, z)) {
                this.checks++;
                continue;
            }
            double minY = Math.max(this.minFeetY, origin.location.y() - this.options.verticalRadius());
            double maxY = Math.min(this.maxFeetY, origin.location.y() + this.options.verticalRadius());
            Column column = new Column(x, z, minY, maxY, !this.ceiling);
            CompletableFuture<ColumnResult> searched = this.searchColumn(column);
            if (!searched.isDone()) {
                return searched.thenComposeAsync(result -> {
                    if (result == ColumnResult.FOUND) return CompletableFuture.completedFuture(column.location);
                    if (result == ColumnResult.REJECTED) return NOT_FOUND;
                    return this.nearby(origin, columns);
                }, this.executor);
            }
            ColumnResult result = searched.getNow(null);
            if (result == ColumnResult.FOUND) return CompletableFuture.completedFuture(column.location);
            if (result == ColumnResult.REJECTED) return NOT_FOUND;
        }
        return NOT_FOUND;
    }

    private CompletableFuture<ColumnResult> searchColumn(Column column) {
        if (!this.canCheck()) return MISS;
        CompletableFuture<Boolean> ready = this.chunks.prepare(column.x, column.z, column.x, column.z);
        if (ready.isDone()) return this.scanPreparedColumn(column, ready.getNow(false));
        return ready.thenComposeAsync(available -> this.scanPreparedColumn(column, available), this.executor);
    }

    private CompletableFuture<ColumnResult> scanPreparedColumn(Column column, boolean available) {
        if (!this.canCheck() || !available) return MISS;
        // 高度图跳过上方空气, 下界仍需从逻辑顶层以下穿过岩体寻找空腔
        int surface = this.chunks.getChunkForCollisions(column.x >> 4, column.z >> 4)
                .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.x & 15, column.z & 15);
        column.y = this.ceiling ? this.maxY - 1 : Math.min(surface, this.maxY - 1);
        if (surface < Math.max(this.chunks.getMinY(), Mth.floor(column.minY) - 1)) {
            this.checks++;
            return MISS;
        }
        return this.scanColumn(column);
    }

    private CompletableFuture<ColumnResult> scanColumn(Column column) {
        int bottom = Math.max(this.chunks.getMinY(), Mth.floor(column.minY) - 1);
        while (this.canCheck() && column.y >= bottom) {
            int y = column.y--;
            this.checks++;
            this.position.set(column.x, y, column.z);
            BlockState state = this.chunks.getBlockState(this.position);
            if (!state.getFluidState().isEmpty()) return MISS;
            if (state.isAir() || state.getBlock() instanceof LeavesBlock) {
                column.open = true;
                continue;
            }
            if (!column.open) continue;
            // 只有可能提供支撑的列才准备完整碰撞范围, 同一候选不重复扣检查次数
            CompletableFuture<Boolean> ready = this.chunks.prepare(this.dimensions, column.x + 0.5, column.z + 0.5);
            if (!ready.isDone()) {
                return ready.thenComposeAsync(available -> {
                    if (this.completion.isDone() || !available) return MISS;
                    return this.inspectFloor(column, y, state);
                }, this.executor)
                        .thenComposeAsync(result -> result == ColumnResult.SKIP ? this.scanColumn(column) : CompletableFuture.completedFuture(result), this.executor);
            }
            if (this.completion.isDone() || !ready.getNow(false)) return MISS;
            CompletableFuture<ColumnResult> inspected = this.inspectFloor(column, y, state);
            if (!inspected.isDone()) {
                return inspected.thenComposeAsync(result -> result == ColumnResult.SKIP ? this.scanColumn(column) : CompletableFuture.completedFuture(result), this.executor);
            }
            if (inspected.getNow(null) != ColumnResult.SKIP) return inspected;
        }
        return MISS;
    }

    private CompletableFuture<ColumnResult> inspectFloor(Column column, int blockY, BlockState state) {
        this.position.set(column.x, blockY, column.z);
        VoxelShape shape = state.getCollisionShape(this.leaflessBlocks, this.position);
        if (shape.isEmpty()) return SKIP;
        double x = column.x + 0.5;
        double z = column.z + 0.5;
        int cellY = blockY + Mth.floor(shape.max(Direction.Axis.Y));
        double y = TeleportSafety.getStandingY(this.leaflessBlocks, this.dimensions, x, cellY, z);
        if (y < column.minY || y > column.maxY) return MISS;
        if (this.hasFluid(x, y, z)) return MISS;
        column.location = new WorldLocation(this.area.world(), x, y, z, 0, 0);
        // 站立高度可能在无支撑时返回格底, 最终支撑也必须来自非树叶方块
        if (!this.hasNonLeafSupport(x, y, z)) return BLOCKED;
        return this.rules.check(column.location)
                .thenApply(reason -> {
                    if (reason == null) return ColumnResult.FOUND;
                    // 物理问题在附近补救, 业务规则拒绝则交回外层重抽
                    return this.rules.rejectedRule() instanceof SafeLocationRule ? ColumnResult.BLOCKED : ColumnResult.REJECTED;
                });
    }

    private boolean hasNonLeafSupport(double x, double y, double z) {
        double halfWidth = this.dimensions.width() / 2;
        double halfDepth = this.dimensions.depth() / 2;
        AABB support = new AABB(x - halfWidth + 1.0E-7, y - 1.0E-5, z - halfDepth + 1.0E-7, x + halfWidth - 1.0E-7, y, z + halfDepth - 1.0E-7);
        return this.leaflessBlocks.getBlockCollisions(null, support).iterator().hasNext();
    }

    private boolean hasFluid(double x, double y, double z) {
        double minX = x - this.dimensions.width() / 2 + 1.0E-7;
        double maxX = x + this.dimensions.width() / 2 - 1.0E-7;
        double minZ = z - this.dimensions.depth() / 2 + 1.0E-7;
        double maxZ = z + this.dimensions.depth() / 2 - 1.0E-7;
        for (int blockX = Mth.floor(minX); blockX <= Mth.floor(maxX); blockX++) {
            for (int blockZ = Mth.floor(minZ); blockZ <= Mth.floor(maxZ); blockZ++) {
                for (int blockY = Mth.floor(y + 1.0E-7); blockY <= Mth.floor(y + this.dimensions.height() - 1.0E-7); blockY++) {
                    this.position.set(blockX, blockY, blockZ);
                    FluidState fluid = this.chunks.getFluidState(this.position);
                    if (!fluid.isEmpty() && y + 1.0E-7 < blockY + fluid.getHeight(this.chunks, this.position)) return true;
                }
            }
        }
        return false;
    }

    private boolean inside(int blockX, int blockZ) {
        double x = blockX + 0.5;
        double z = blockZ + 0.5;
        if (!this.area.contains(x, z)) return false;
        WorldBorder border = this.chunks.getWorldBorder();
        double halfWidth = this.dimensions.width() / 2;
        double halfDepth = this.dimensions.depth() / 2;
        return x - halfWidth >= border.getMinX() && x + halfWidth <= border.getMaxX()
                && z - halfDepth >= border.getMinZ() && z + halfDepth <= border.getMaxZ();
    }

    private boolean canCheck() {
        return !this.completion.isDone() && this.checks < this.options.maxChecks();
    }

    private enum ColumnResult { MISS, SKIP, BLOCKED, REJECTED, FOUND }

    private static final class Column {
        private final int x;
        private final int z;
        private final double minY;
        private final double maxY;
        private int y;
        private boolean open;
        private WorldLocation location;

        private Column(int x, int z, double minY, double maxY, boolean open) {
            this.x = x;
            this.z = z;
            this.minY = minY;
            this.maxY = maxY;
            this.open = open;
        }
    }

    private static final class NearbyColumns {
        private static final Comparator<Offset> ORDER = Comparator.comparingLong((Offset offset) -> (long) offset.x * offset.x + (long) offset.z * offset.z)
                .thenComparingInt(Offset::x)
                .thenComparingInt(Offset::z);

        private final int radius;
        private final PriorityQueue<Offset> queue = new PriorityQueue<>(ORDER);
        private final LongOpenHashSet visited = new LongOpenHashSet();

        private NearbyColumns(int radius) {
            this.radius = radius;
            this.visited.add(0L);
            this.expand(0, 0);
        }

        private Offset next() {
            Offset next = this.queue.remove();
            this.expand(next.x, next.z);
            return next;
        }

        private void expand(int x, int z) {
            this.offer(x - 1, z);
            this.offer(x + 1, z);
            this.offer(x, z - 1);
            this.offer(x, z + 1);
        }

        private void offer(int x, int z) {
            if (Math.abs((long) x) > this.radius || Math.abs((long) z) > this.radius) return;
            if (this.visited.add((x & 0xffffffffL) | ((long) z << 32))) {
                this.queue.add(new Offset(x, z));
            }
        }
    }

    private record Offset(int x, int z) {
    }
}
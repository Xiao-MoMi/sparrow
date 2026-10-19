package net.momirealms.sparrow.teleport.search;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.Dimensions;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.craftbukkit.CraftChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SearchChunks implements CollisionGetter {
    private static final CompletableFuture<Boolean> READY = CompletableFuture.completedFuture(true);        // 范围内区块均已可用
    private static final CompletableFuture<Boolean> UNAVAILABLE = CompletableFuture.completedFuture(false); // 策略不允许取得范围内的全部区块

    private final ServerLevel world; // 此缓存所属的目标世界
    private final ChunkPolicy policy; // 本次搜索固定使用的加载与生成策略
    private final Long2ObjectOpenHashMap<CompletableFuture<@Nullable LevelChunk>> chunks = new Long2ObjectOpenHashMap<>(); // 区块坐标 -> 加载任务, 正常结果为 null 表示未生成

    public SearchChunks(@NotNull ServerLevel world, @NotNull ChunkPolicy policy) {
        this.world = world;
        this.policy = policy;
    }

    /**
     * 准备给定 X/Z 处检查方块碰撞所需的区块.
     * 范围包含碰撞箱的水平投影及外围一格, 可读取相邻方块伸出的碰撞形状.
     * 一个区块包含完整高度, 同一 X/Z 下检查不同 Y 可以复用这次准备结果.
     *
     * @param dimensions 用于确定水平检测范围的实体尺寸
     * @param x 候选脚底中心的 X 坐标, 必须有限且位于世界坐标范围内
     * @param z 候选脚底中心的 Z 坐标, 必须有限且位于世界坐标范围内
     * @return 准备结果, true 表示所需区块均可用, false 表示有未生成区块且策略禁止生成, 异常完成表示加载失败
     */
    @NotNull
    public CompletableFuture<Boolean> prepare(@NotNull Dimensions dimensions, double x, double z) {
        // 碰撞遍历会将体积边界按容差取整, 再向外读取相邻一格.
        int minX = Mth.floor(x - dimensions.width() / 2 - 1.0E-7) - 1;
        int maxX = Mth.floor(x + dimensions.width() / 2 + 1.0E-7) + 1;
        int minZ = Mth.floor(z - dimensions.depth() / 2 - 1.0E-7) - 1;
        int maxZ = Mth.floor(z + dimensions.depth() / 2 + 1.0E-7) + 1;
        return this.prepare(minX, minZ, maxX, maxZ);
    }

    /**
     * 准备方块坐标闭区间覆盖的区块, 重叠范围会复用同一份加载任务和结果.
     *
     * @param minBlockX 范围最小 X, 必须不大于 maxBlockX
     * @param minBlockZ 范围最小 Z, 必须不大于 maxBlockZ
     * @param maxBlockX 范围最大 X
     * @param maxBlockZ 范围最大 Z
     * @return 准备结果, true 表示全部可用, false 表示有未生成区块且策略禁止生成, 异常完成表示加载失败
     */
    @NotNull
    public CompletableFuture<Boolean> prepare(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int minX = minBlockX >> 4;
        int maxX = maxBlockX >> 4;
        int minZ = minBlockZ >> 4;
        int maxZ = maxBlockZ >> 4;
        List<CompletableFuture<@Nullable LevelChunk>> pending = null;
        boolean available = true;
        // 已完成的正常结果直接读取, 其余任务统一等待, 包括需要向上传播的异常结果.
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                long key = ChunkPos.asLong(chunkX, chunkZ);
                CompletableFuture<@Nullable LevelChunk> chunk = this.chunks.get(key);
                if (chunk == null) {
                    chunk = this.load(chunkX, chunkZ);
                    this.chunks.put(key, chunk);
                }
                if (!chunk.isDone() || chunk.isCompletedExceptionally()) {
                    if (pending == null) pending = new ArrayList<>();
                    pending.add(chunk);
                } else {
                    available &= chunk.getNow(null) != null;
                }
            }
        }
        if (pending == null) return available ? READY : UNAVAILABLE;
        // allOf 保留加载异常, 只有全部任务正常完成后才判断是否存在未生成区块.
        List<CompletableFuture<@Nullable LevelChunk>> required = pending;
        boolean present = available;
        return CompletableFuture.allOf(required.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> present && this.available(required));
    }

    // 检查一组已经正常完成的加载任务是否都取得了区块.
    private boolean available(@NotNull List<CompletableFuture<@Nullable LevelChunk>> required) {
        for (int index = 0, size = required.size(); index < size; index++) {
            if (required.get(index).getNow(null) == null) return false;
        }
        return true;
    }

    /**
     * 按平台支持的方式取得一个完整区块, 已加载区块直接返回.
     * Paper 使用异步区块接口, Spigot 的区块源操作通过插件调度器切换到所需线程.
     *
     * @param x 区块 X 坐标
     * @param z 区块 Z 坐标
     * @return 加载任务, 正常结果为 null 表示未生成且策略禁止生成, 异常完成表示加载失败
     */
    @NotNull
    private CompletableFuture<@Nullable LevelChunk> load(int x, int z) {
        LevelChunk loaded = VersionHelper.hasPaperPatch ? this.world.getChunkSource().getChunkAtIfLoadedImmediately(x, z) : this.world.getChunkSource().getChunkNow(x, z);
        if (loaded != null) return CompletableFuture.completedFuture(loaded);
        if (VersionHelper.hasPaperPatch) {
            return this.world.getWorld().getChunkAtAsync(x, z, this.policy == ChunkPolicy.ALLOW_GENERATION)
                    .thenApply(chunk -> chunk == null ? null : (LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL));
        }
        if (this.policy == ChunkPolicy.ALLOW_GENERATION) {
            // Spigot 为每个加载任务持有独立且不过期的票据, 完成后在主线程释放.
            TicketType ticket = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
            ChunkPos position = new ChunkPos(x, z);
            return CompletableFuture.runAsync(
                    () -> this.world.getChunkSource().addTicketWithRadius(ticket, position, 0),
                    SparrowPlugin.instance().scheduler().platform()
            ).thenComposeAsync(
                    // 主线程中的 getChunkFuture 会等待区块, 工作线程入口会将请求投递给区块源.
                    ignored -> this.world.getChunkSource().getChunkFuture(x, z, ChunkStatus.FULL, true),
                    SparrowPlugin.instance().scheduler().async()
            ).thenApply(result -> {
                ChunkAccess chunk = result.orElse(null);
                if (chunk == null) throw new IllegalStateException("Failed to load destination chunk " + x + "," + z + ": " + result.getError());
                return (LevelChunk) chunk;
            }).whenCompleteAsync(
                    (chunk, error) -> this.world.getChunkSource().removeTicketWithRadius(ticket, position, 0),
                    SparrowPlugin.instance().scheduler().platform()
            );
        }
        // EMPTY 阶段可以读取磁盘中的已有地形, 未完成生成的区块不推进到 FULL.
        return CompletableFuture.supplyAsync(() -> {
            ChunkAccess chunk = this.world.getChunkSource().getChunk(x, z, ChunkStatus.EMPTY, true);
            if (chunk instanceof ImposterProtoChunk wrapper) return wrapper.getWrapped();
            return chunk instanceof LevelChunk full ? full : null;
        }, SparrowPlugin.instance().scheduler().platform());
    }

    @Override
    public int getMinY() {
        return this.world.getMinY();
    }

    @Override
    public int getHeight() {
        return this.world.getHeight();
    }

    @NotNull
    @Override
    public WorldBorder getWorldBorder() {
        return this.world.getWorldBorder();
    }

    // 从缓存取得已经加载的完整区块, 不等待尚未完成的加载.
    @Nullable
    @Override
    public LevelChunk getChunkForCollisions(int x, int z) {
        CompletableFuture<@Nullable LevelChunk> chunk = this.chunks.get(ChunkPos.asLong(x, z));
        return chunk == null ? null : chunk.getNow(null);
    }

    // 读取已准备区块中的方块状态.
    @NotNull
    @Override
    public BlockState getBlockState(@NotNull BlockPos position) {
        return this.getChunkForCollisions(position.getX() >> 4, position.getZ() >> 4).getBlockState(position);
    }

    @Nullable
    @Override
    public BlockState getBlockStateIfLoaded(@NotNull BlockPos position) {
        LevelChunk chunk = this.getChunkForCollisions(position.getX() >> 4, position.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(position);
    }

    // 读取已准备区块中指定方块所包含的流体状态.
    @NotNull
    @Override
    public FluidState getFluidState(@NotNull BlockPos position) {
        return this.getBlockState(position).getFluidState();
    }

    @Nullable
    @Override
    public FluidState getFluidIfLoaded(@NotNull BlockPos position) {
        BlockState state = this.getBlockStateIfLoaded(position);
        return state == null ? null : state.getFluidState();
    }

    // 读取已准备区块中已经实例化的方块实体.
    @Nullable
    @Override
    public BlockEntity getBlockEntity(@NotNull BlockPos position) {
        return this.getChunkForCollisions(position.getX() >> 4, position.getZ() >> 4).getBlockEntities().get(position);
    }

    // 为方块碰撞查询提供空的实体碰撞集合, 此区块视图的检查范围仅包含地形.
    @NotNull
    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, @NotNull AABB bounds) {
        return List.of();
    }
}
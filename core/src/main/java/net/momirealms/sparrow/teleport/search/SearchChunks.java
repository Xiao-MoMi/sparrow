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
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.Dimensions;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.craftbukkit.CraftChunk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class SearchChunks implements CollisionGetter {
    private static final CompletableFuture<Boolean> READY = CompletableFuture.completedFuture(true);
    private static final CompletableFuture<Boolean> UNAVAILABLE = CompletableFuture.completedFuture(false);

    private final ServerLevel world;
    private final boolean generate; // 是否允许生成新地形
    private final Long2ObjectOpenHashMap<CompletableFuture<LevelChunk>> chunks = new Long2ObjectOpenHashMap<>(); // 搜索期间复用加载任务和已完成的区块引用

    public SearchChunks(@NotNull ServerLevel world, boolean generate) {
        this.world = world;
        this.generate = generate;
    }

    // 碰撞查询覆盖检测体积及外围一格, 包含相邻方块伸出的形状
    @NotNull
    public CompletableFuture<Boolean> prepare(@NotNull Dimensions dimensions, double x, double z) {
        int minX = Mth.floor(x - dimensions.width() / 2 - 1.0E-7) - 1;
        int maxX = Mth.floor(x + dimensions.width() / 2 + 1.0E-7) + 1;
        int minZ = Mth.floor(z - dimensions.depth() / 2 - 1.0E-7) - 1;
        int maxZ = Mth.floor(z + dimensions.depth() / 2 + 1.0E-7) + 1;
        return this.prepare(minX, minZ, maxX, maxZ);
    }

    // 准备方块坐标闭区间覆盖的区块, 正常完成值表示全部区块是否可用, 加载异常沿 Future 返回
    @NotNull
    public CompletableFuture<Boolean> prepare(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int minX = minBlockX >> 4;
        int maxX = maxBlockX >> 4;
        int minZ = minBlockZ >> 4;
        int maxZ = maxBlockZ >> 4;
        List<CompletableFuture<LevelChunk>> pending = null;
        boolean available = true;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                // 低 32 位存区块 X, 高 32 位存区块 Z, 负坐标按位保留
                long key = (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
                CompletableFuture<LevelChunk> chunk = this.chunks.get(key);
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
        List<CompletableFuture<LevelChunk>> required = pending;
        boolean present = available;
        return CompletableFuture.allOf(required.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> present && this.available(required));
    }

    private boolean available(List<CompletableFuture<LevelChunk>> required) {
        boolean available = true;
        for (int i = 0, size = required.size(); i < size; i++) {
            available &= required.get(i).getNow(null) != null;
        }
        return available;
    }

    // 优先复用已加载的完整区块, 其余按平台支持的方式加载
    private CompletableFuture<LevelChunk> load(int x, int z) {
        LevelChunk loaded = getLoaded(this.world, x, z);
        if (loaded != null) return CompletableFuture.completedFuture(loaded);
        if (VersionHelper.hasPaperPatch) {
            return this.world.getWorld().getChunkAtAsync(x, z, this.generate)
                    .thenApply(chunk -> chunk == null ? null : (LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL));
        }
        if (this.generate) {
            // 原版 UNKNOWN 票据会在生成结束前过期, 每次加载单独持有票据, 完成后释放
            TicketType<?> ticket = new TicketType<>(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);
            ChunkPos position = new ChunkPos(x, z);
            return CompletableFuture.runAsync(
                    () -> this.world.getChunkSource().addTicketWithRadius(ticket, position, 0),
                    SparrowPlugin.instance().scheduler().platform()
            ).thenComposeAsync(
                    // 主线程调用 getChunkFuture 会等待区块, 因此从工作线程发起
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
        // Spigot 在主线程取得 EMPTY 阶段结果, 接受完整区块及其包装
        return CompletableFuture.supplyAsync(() -> {
            ChunkAccess chunk = this.world.getChunkSource().getChunk(x, z, ChunkStatus.EMPTY, true);
            if (chunk instanceof ImposterProtoChunk wrapper) return wrapper.getWrapped();
            return chunk instanceof LevelChunk full ? full : null;
        }, SparrowPlugin.instance().scheduler().platform());
    }

    @Nullable
    private static LevelChunk getLoaded(ServerLevel world, int x, int z) {
        return VersionHelper.hasPaperPatch ? world.getChunkSource().getChunkAtIfLoadedImmediately(x, z) : world.getChunkSource().getChunkNow(x, z);
    }

    // 查询范围须已成功 prepare, 高度裁剪到世界范围后检查闭区间内是否全为空气
    public boolean isAir(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        minY = Math.max(minY, this.getMinY());
        maxY = Math.min(maxY, this.getMinY() + this.getHeight() - 1);
        int firstSection = this.getMinY() >> 4;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = this.getChunkForCollisions(chunkX, chunkZ);
                for (int sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
                    // 整节全空气可直接跳过, 部分重叠的区块节只检查与查询范围的交集
                    LevelChunkSection section = chunk.getSections()[sectionY - firstSection];
                    if (section.hasOnlyAir()) continue;
                    int fromX = Math.max(minX, chunkX << 4);
                    int toX = Math.min(maxX, (chunkX << 4) + 15);
                    int fromZ = Math.max(minZ, chunkZ << 4);
                    int toZ = Math.min(maxZ, (chunkZ << 4) + 15);
                    int fromY = Math.max(minY, sectionY << 4);
                    int toY = Math.min(maxY, (sectionY << 4) + 15);
                    for (int x = fromX; x <= toX; x++) {
                        for (int z = fromZ; z <= toZ; z++) {
                            for (int y = fromY; y <= toY; y++) {
                                if (!section.getBlockState(x & 15, y & 15, z & 15).isAir()) return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public int getMinY() {
        return this.world.getMinY();
    }

    @Override
    public int getHeight() {
        return this.world.getHeight();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.world.getWorldBorder();
    }

    // 读取缓存中的完整区块, 缓存缺失, 加载未完成或区块不可用时返回 null.
    @Nullable
    @Override
    public LevelChunk getChunkForCollisions(int x, int z) {
        CompletableFuture<LevelChunk> chunk = this.chunks.get((x & 0xffffffffL) | ((long) z << 32));
        return chunk == null ? null : chunk.getNow(null);
    }

    // 读取已准备区块中的方块状态, 对应范围的 {@code prepare} <strong>必须已成功完成</strong>.
    @Override
    public BlockState getBlockState(BlockPos position) {
        return this.getChunkForCollisions(position.getX() >> 4, position.getZ() >> 4).getBlockState(position);
    }

    @Nullable
    @Override
    public BlockState getBlockStateIfLoaded(BlockPos position) {
        LevelChunk chunk = this.getChunkForCollisions(position.getX() >> 4, position.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(position);
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return this.getBlockState(position).getFluidState();
    }

    @Nullable
    @Override
    public FluidState getFluidIfLoaded(BlockPos position) {
        BlockState state = this.getBlockStateIfLoaded(position);
        return state == null ? null : state.getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        // 只读取已实例化的方块实体, 搜索过程不解析待加载 NBT 或注册实体
        return this.getChunkForCollisions(position.getX() >> 4, position.getZ() >> 4).getBlockEntities().get(position);
    }

    // 落点检测只考虑方块碰撞
    @NotNull
    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB bounds) {
        return List.of();
    }
}
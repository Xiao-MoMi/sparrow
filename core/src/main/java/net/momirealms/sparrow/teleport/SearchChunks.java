package net.momirealms.sparrow.teleport;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.craftbukkit.CraftChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class SearchChunks implements CollisionGetter {
    private final ServerLevel world;
    private final TeleportDimensions dimensions;
    private final Long2ObjectOpenHashMap<CompletableFuture<LevelChunk>> chunks = new Long2ObjectOpenHashMap<>(); // 搜索期间保留区块对象, 供方块与碰撞查询直接读取

    SearchChunks(ServerLevel world, TeleportDimensions dimensions) {
        this.world = world;
        this.dimensions = dimensions;
    }

    CompletableFuture<Boolean> prepare(double x, double z) {
        int minX = (Mth.floor(x - this.dimensions.width() / 2 - 1.0E-7) - 1) >> 4;
        int maxX = (Mth.floor(x + this.dimensions.width() / 2 + 1.0E-7) + 1) >> 4;
        int minZ = (Mth.floor(z - this.dimensions.depth() / 2 - 1.0E-7) - 1) >> 4;
        int maxZ = (Mth.floor(z + this.dimensions.depth() / 2 + 1.0E-7) + 1) >> 4;
        List<CompletableFuture<LevelChunk>> required = new ArrayList<>();
        boolean ready = true;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                long key = (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
                CompletableFuture<LevelChunk> chunk = this.chunks.get(key);
                if (chunk == null) {
                    chunk = this.load(chunkX, chunkZ);
                    this.chunks.put(key, chunk);
                }
                required.add(chunk);
                ready &= chunk.isDone();
            }
        }
        if (ready) return CompletableFuture.completedFuture(required).thenApply(this::available);
        return CompletableFuture.allOf(required.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> this.available(required));
    }

    private boolean available(List<CompletableFuture<LevelChunk>> required) {
        boolean available = true;
        for (int i = 0, size = required.size(); i < size; i++) {
            available &= required.get(i).join() != null;
        }
        return available;
    }

    private CompletableFuture<LevelChunk> load(int x, int z) {
        if (VersionHelper.hasPaperPatch) {
            return this.world.getWorld().getChunkAtAsync(x, z, false)
                    .thenApply(chunk -> chunk == null ? null : (LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL));
        }
        // Spigot 在平台线程加载磁盘区块, EMPTY 阶段不会生成地形, 已保存的完整区块会返回 LevelChunk 或其包装
        return CompletableFuture.supplyAsync(() -> {
            ChunkAccess chunk = this.world.getChunkSource().getChunk(x, z, ChunkStatus.EMPTY, true);
            if (chunk instanceof ImposterProtoChunk wrapper) return wrapper.getWrapped();
            return chunk instanceof LevelChunk loaded ? loaded : null;
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

    @Override
    public WorldBorder getWorldBorder() {
        return this.world.getWorldBorder();
    }

    @Nullable
    @Override
    public LevelChunk getChunkForCollisions(int x, int z) {
        CompletableFuture<LevelChunk> chunk = this.chunks.get((x & 0xffffffffL) | ((long) z << 32));
        return chunk == null ? null : chunk.getNow(null);
    }

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
        return this.getChunkForCollisions(position.getX() >> 4, position.getZ() >> 4).getBlockEntity(position);
    }

    @NotNull
    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB bounds) {
        return List.of();
    }
}
package net.momirealms.sparrow.teleport.processor.target;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public record GeneratedChunk() implements PositionProcessor {
    public static final SparrowKey TYPE = SparrowKey.sparrow("generated-chunk");

    @NotNull
    public static GeneratedChunk fromNbt(@NotNull CompoundTag tag) {
        return new GeneratedChunk();
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

    @NotNull
    @Override
    public CompletableFuture<Result> process(@NotNull TeleportContext context, @NotNull WorldLocation location) {
        Location resolved = location.resolve();
        if (resolved == null) return CompletableFuture.completedFuture(new Result.Reject(MessageConstants.TELEPORT_REJECTED_INVALID));
        ServerChunkCache chunks = ((CraftWorld) resolved.getWorld()).getHandle().getChunkSource();
        // 方块坐标右移 4 位就是区块坐标
        int x = resolved.getBlockX() >> 4;
        int z = resolved.getBlockZ() >> 4;
        // 已经加载的区块一定生成完毕了
        boolean loaded = chunks.getChunkAtIfLoadedImmediately(x, z) != null;
        if (loaded) return CompletableFuture.completedFuture(Result.Accept.INSTANCE);
        // 异步读取区域文件里保存的状态, FULL 表示已经生成完毕. 文件里没有这个区块, 就是从来没生成过
        return chunks.chunkMap.read(new ChunkPos(x, z))
                .thenApply(data -> {
                    boolean generated = data.isPresent() && data.get().read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY) == ChunkStatus.FULL;
                    return generated ? Result.Accept.INSTANCE : new Result.Reject(MessageConstants.TELEPORT_REJECTED_UNGENERATED);
                });
    }
}
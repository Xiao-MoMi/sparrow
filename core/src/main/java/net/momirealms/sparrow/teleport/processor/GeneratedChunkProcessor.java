package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.teleport.rule.RequestContext;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class GeneratedChunkProcessor implements TeleportProcessor.RequestRule {
    public static final SparrowKey TYPE = SparrowKey.sparrow("generated-chunk");

    @NotNull
    @Override
    public CompletableFuture<Component> check(@NotNull RequestContext context) {
        if (context.origin() == null) throw new IllegalArgumentException("generated-chunk requires an original point");
        Location location = context.origin().resolve();
        if (location == null) return PASS;
        ServerChunkCache chunks = ((CraftWorld) location.getWorld()).getHandle().getChunkSource();
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        return CompletableFuture.supplyAsync(
                () -> VersionHelper.hasPaperPatch ? chunks.getChunkAtIfLoadedImmediately(x, z) != null : chunks.getChunkNow(x, z) != null,
                VersionHelper.hasPaperPatch ? SparrowPlugin.instance().scheduler().async() : SparrowPlugin.instance().scheduler().platform()
        ).thenCompose(loaded -> {
            if (loaded) return PASS;
            // 保存状态通过区域文件 I/O 读取, 用来判断区块是否完成生成
            return chunks.chunkMap.read(new ChunkPos(x, z))
                    .thenApply(data ->
                            data.isPresent() && data.get().read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY) == ChunkStatus.FULL
                                    ? null
                                    : MessageConstants.TELEPORT_REJECTED_UNGENERATED
                    );
        });
    }
}
package net.momirealms.sparrow.teleport.search;

import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.Dimensions;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

public final class SearchContext {
    private final TeleportContext teleportContext;  // 本次传送的业务数据, 规则只读
    private final World world;                      // 已解析的目标世界, 同次搜索的候选都属于此世界
    private final Dimensions dimensions;            // 构造时从传送上下文解码一次的尺寸
    private final SearchChunks chunks;              // 本次搜索独享, 各个候选和规则共享的区块缓存

    public SearchContext(@NotNull TeleportContext teleportContext, @NotNull World world, @NotNull ChunkPolicy chunkPolicy) {
        this.teleportContext = teleportContext;
        this.world = world;
        this.dimensions = teleportContext.getDimensions();
        this.chunks = new SearchChunks(((CraftWorld) world).getHandle(), chunkPolicy);
    }

    @NotNull
    public TeleportContext teleportContext() {
        return this.teleportContext;
    }

    @NotNull
    public World world() {
        return this.world;
    }

    @NotNull
    public Dimensions dimensions() {
        return this.dimensions;
    }

    @NotNull
    public SearchChunks chunks() {
        return this.chunks;
    }
}
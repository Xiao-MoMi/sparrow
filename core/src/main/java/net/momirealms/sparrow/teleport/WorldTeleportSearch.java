package net.momirealms.sparrow.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class WorldTeleportSearch {
    private final WorldLocation origin;
    private final int height;
    private final int maxY;
    private final SearchChunks chunks;

    public WorldTeleportSearch(@NotNull Location origin, int height) {
        this.origin = WorldLocation.from(origin);
        this.height = height;
        ServerLevel world = ((CraftWorld) origin.getWorld()).getHandle();
        this.maxY = world.getMinY() + world.getHeight();
        this.chunks = new SearchChunks(world, true);
    }

    @NotNull
    public CompletableFuture<@Nullable WorldLocation> find() {
        int x = Mth.floor(this.origin.x());
        int z = Mth.floor(this.origin.z());
        // 相邻方块参与碰撞形状计算, 整个查询只读取预先准备的区块
        return CompletableFuture.completedFuture(this.chunks)
                .thenCompose(chunks -> chunks.prepare(x - 1, z - 1, x + 1, z + 1))
                .thenApplyAsync(available -> available ? this.scan(x, z) : null, SparrowPlugin.instance().scheduler().async());
    }

    @Nullable
    private WorldLocation scan(int x, int z) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int space = 0;
        for (double y = this.origin.y(); y < this.maxY; y++) {
            position.set(x, Mth.floor(y), z);
            BlockState state = this.chunks.getBlockState(position);
            space = state.getCollisionShape(this.chunks, position).isEmpty() && !state.liquid() ? space + 1 : 0;
            if (space >= this.height) {
                return new WorldLocation(this.origin.world(), this.origin.x(), y - (this.height - 1), this.origin.z(), this.origin.yaw(), this.origin.pitch());
            }
        }
        return null;
    }
}
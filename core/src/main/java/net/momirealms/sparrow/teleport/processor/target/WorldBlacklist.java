package net.momirealms.sparrow.teleport.processor.target;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.ListTag;
import net.momirealms.sparrow.nbt.NBT;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public record WorldBlacklist(@NotNull List<String> worlds, @NotNull String bypassPermission) implements PositionProcessor {
    public static final SparrowKey TYPE = SparrowKey.sparrow("world-blacklist");

    public WorldBlacklist() {
        this(List.of(), DependencyVersions.PROJECT_ID + ".bypass.teleport-world-blacklist");
    }

    @NotNull
    public static WorldBlacklist fromNbt(@NotNull CompoundTag tag) {
        ListTag names = tag.getList("worlds", new ListTag());
        List<String> worlds = new ArrayList<>(names.size());
        for (int index = 0, size = names.size(); index < size; index++) {
            worlds.add(names.getString(index));
        }
        return new WorldBlacklist(worlds, tag.getString("bypass_permission", DependencyVersions.PROJECT_ID + ".bypass.teleport-world-blacklist"));
    }

    @NotNull
    @Override
    public SparrowKey type() {
        return TYPE;
    }

    @NotNull
    @Override
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag worlds = new ListTag();
        for (String world : this.worlds) {
            worlds.add(NBT.createString(world));
        }
        tag.put("worlds", worlds);
        tag.putString("bypass_permission", this.bypassPermission);
        return tag;
    }

    @NotNull
    @Override
    public CompletableFuture<Result> process(@NotNull TeleportContext context, @NotNull WorldLocation location) {
        if (!this.worlds.contains(location.world())) return CompletableFuture.completedFuture(Result.Accept.INSTANCE);
        if (this.bypassPermission.isEmpty()) return CompletableFuture.completedFuture(new Result.Reject(MessageConstants.TELEPORT_REJECTED_WORLD));
        // 处理器跑在目标服上, 被传送的玩家这时还在源服, 按 UUID 异步查询权限
        return SparrowPlugin.instance().compatibilityManager()
                .hasPermissionAsync(context.toNbt().getUUID(TeleportContext.PLAYER_UUID), this.bypassPermission)
                .thenApply(bypass -> bypass ? Result.Accept.INSTANCE : new Result.Reject(MessageConstants.TELEPORT_REJECTED_WORLD));
    }
}
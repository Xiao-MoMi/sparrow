package net.momirealms.sparrow.teleport.position;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PlayerPos implements TeleportPosition {
    static final SparrowKey TYPE = SparrowKey.sparrow("player");

    private final @Nullable UUID playerId;     // 目标玩家的 UUID, 用名字指定时为 null
    private final @Nullable String playerName; // 目标玩家的名字, 用 UUID 指定时为 null

    PlayerPos(@NotNull UUID playerId) {
        this.playerId = playerId;
        this.playerName = null;
    }

    PlayerPos(@NotNull String playerName) {
        this.playerId = null;
        this.playerName = playerName;
    }

    @NotNull
    static PlayerPos fromNbt(@NotNull CompoundTag tag) {
        if (tag.containsKey("uuid")) {
            UUID playerId = tag.getUUID("uuid");
            if (playerId == null) throw new IllegalArgumentException("Player position requires a valid UUID");
            return new PlayerPos(playerId);
        }
        String name = tag.getString("name", null);
        if (name == null) throw new IllegalArgumentException("Player position requires a UUID or name");
        return new PlayerPos(name);
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
        if (this.playerId != null) {
            tag.putUUID("uuid", this.playerId);
        } else {
            tag.putString("name", this.playerName);
        }
        return tag;
    }

    @NotNull
    @Override
    public CompletableFuture<Result> resolve(@NotNull TeleportContext context) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer target;
        if (this.playerId != null) {
            target = plugin.playerManager().getOnlinePlayer(this.playerId);
        } else {
            Player player = Bukkit.getPlayerExact(this.playerName);
            target = player == null ? null : plugin.playerManager().getOnlinePlayer(player);
        }
        // 在异步线程上直接读坐标, 可能比对方最新的位置晚一点点, 对传送来说足够了
        Result result = target == null
                ? new Result.Fail(MessageConstants.TELEPORT_REJECTED_PLAYER)
                : new Result.Success(WorldLocation.from(target.platformPlayer.getLocation()));
        return CompletableFuture.completedFuture(result);
    }
}

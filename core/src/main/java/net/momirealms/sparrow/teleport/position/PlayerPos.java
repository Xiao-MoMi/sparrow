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

public final class PlayerPos implements TeleportPosition {
    public static final SparrowKey TYPE = SparrowKey.sparrow("player");

    private final @Nullable UUID playerId;
    private final @Nullable String playerName;

    public PlayerPos(@NotNull UUID playerId) {
        this.playerId = playerId;
        this.playerName = null;
    }

    public PlayerPos(@NotNull String playerName) {
        this.playerId = null;
        this.playerName = playerName;
    }

    @NotNull
    public static PlayerPos fromNbt(@NotNull CompoundTag tag) {
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
            target = plugin.playerManager().getPlayer(this.playerId);
        } else {
            Player player = Bukkit.getPlayerExact(this.playerName);
            target = player == null ? null : plugin.playerManager().getPlayer(player);
        }
        Result result = target == null
                ? new Result.Fail(MessageConstants.TELEPORT_REJECTED_PLAYER)
                : new Result.Success(WorldLocation.from(target.platformPlayer().getLocation()));
        return CompletableFuture.completedFuture(result);
    }
}
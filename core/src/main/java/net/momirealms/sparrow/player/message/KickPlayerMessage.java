package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.event.player.PlayerKickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class KickPlayerMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "kick_player");
    public static final MessageCodec<FriendlyByteBuf, KickPlayerMessage> CODEC = RedisMessage.codec(KickPlayerMessage::write, KickPlayerMessage::new);

    private final UUID playerId;
    private final Component reason;

    public KickPlayerMessage(@NotNull UUID playerId, @NotNull Component reason) {
        this.playerId = playerId;
        this.reason = reason;
    }

    private KickPlayerMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.reason = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, 262144));
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(this.reason), 262144);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer player = plugin.playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return;
        PlatformExecutor executor = plugin.scheduler().platform();
        Runnable kick = () -> {
            if (!player.isOnlineLocally()) return;
            player.nmsPlayer.connection.disconnect(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.reason)), PlayerKickEvent.Cause.PLUGIN);
        };
        if (executor.isOwnedByCurrentRegion(player.platformPlayer)) {
            kick.run();
        } else {
            executor.run(kick, () -> {}, player.platformPlayer);
        }
    }
}
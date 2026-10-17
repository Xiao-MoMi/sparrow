package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class HideBossBarMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "hide_boss_bar");
    public static final MessageCodec<FriendlyByteBuf, HideBossBarMessage> CODEC = RedisMessage.codec(HideBossBarMessage::write, HideBossBarMessage::new);

    private final UUID playerId;
    private final UUID bossBarId;

    public HideBossBarMessage(@NotNull UUID playerId, @NotNull UUID bossBarId) {
        this.playerId = playerId;
        this.bossBarId = bossBarId;
    }

    private HideBossBarMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.bossBarId = buffer.readUUID();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        buffer.writeUUID(this.bossBarId);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        BukkitSparrowPlayer player = SparrowPlugin.instance().playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return;
        player.connection.sendPacket(ClientboundBossEventPacket.createRemovePacket(this.bossBarId));
    }
}
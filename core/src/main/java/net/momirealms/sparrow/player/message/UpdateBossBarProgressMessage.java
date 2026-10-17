package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.util.PacketBossEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class UpdateBossBarProgressMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "update_boss_bar_progress");
    public static final MessageCodec<FriendlyByteBuf, UpdateBossBarProgressMessage> CODEC = RedisMessage.codec(UpdateBossBarProgressMessage::write, UpdateBossBarProgressMessage::new);

    private final UUID playerId;
    private final UUID bossBarId;
    private final float progress;

    public UpdateBossBarProgressMessage(@NotNull UUID playerId, @NotNull UUID bossBarId, float progress) {
        this.playerId = playerId;
        this.bossBarId = bossBarId;
        this.progress = progress;
    }

    private UpdateBossBarProgressMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.bossBarId = buffer.readUUID();
        this.progress = buffer.readFloat();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        buffer.writeUUID(this.bossBarId);
        buffer.writeFloat(this.progress);
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
        PacketBossEvent event = new PacketBossEvent(this.bossBarId);
        event.setProgress(this.progress);
        player.connection.sendPacket(ClientboundBossEventPacket.createUpdateProgressPacket(event));
    }
}
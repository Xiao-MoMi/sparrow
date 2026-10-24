package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class SendDemoMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "send_demo");
    public static final MessageCodec<FriendlyByteBuf, SendDemoMessage> CODEC = RedisMessage.codec(SendDemoMessage::write, SendDemoMessage::new);

    private final UUID playerId;

    public SendDemoMessage(@NotNull UUID playerId) {
        this.playerId = playerId;
    }

    private SendDemoMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        BukkitSparrowPlayer player = SparrowPlugin.instance().playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return;
        player.sendDemo();
    }
}
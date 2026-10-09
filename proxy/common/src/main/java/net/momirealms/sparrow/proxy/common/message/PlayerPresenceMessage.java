package net.momirealms.sparrow.proxy.common.message;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.proxy.common.player.PlayerPresence;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PlayerPresenceMessage(@NotNull UUID uuid, @Nullable PlayerPresence player) implements RedisMessage<ByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "proxy_player_presence");
    public static final MessageCodec<ByteBuf, PlayerPresenceMessage> CODEC = RedisMessage.codec(PlayerPresenceMessage::write, PlayerPresenceMessage::new);

    private PlayerPresenceMessage(ByteBuf buffer) {
        this(new UUID(buffer.readLong(), buffer.readLong()), buffer);
    }

    private PlayerPresenceMessage(UUID uuid, ByteBuf buffer) {
        this(uuid, buffer.readBoolean() ? PlayerPresence.read(uuid, buffer) : null);
    }

    private void write(ByteBuf buffer) {
        buffer.writeLong(this.uuid.getMostSignificantBits()).writeLong(this.uuid.getLeastSignificantBits());
        buffer.writeBoolean(this.player != null);
        if (this.player != null) {
            this.player.write(buffer);
        }
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    public void handle(@NotNull MessageBroker<ByteBuf> broker) {
    }
}
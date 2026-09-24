package net.momirealms.sparrow.player.cluster;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

public record PlayerPresenceMessage(@NotNull String serverId, @NotNull UUID uuid, @NotNull String name, boolean joined) implements RedisMessage<ByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "player_presence");
    public static final MessageCodec<ByteBuf, PlayerPresenceMessage> CODEC = RedisMessage.codec(PlayerPresenceMessage::write, PlayerPresenceMessage::new);
    private static volatile @Nullable Consumer<PlayerPresenceMessage> listener;

    private PlayerPresenceMessage(ByteBuf buffer) {
        this(ByteBufHelper.readUtf8(buffer, 32767), new UUID(buffer.readLong(), buffer.readLong()), ByteBufHelper.readUtf8(buffer, 64), buffer.readBoolean());
    }

    private void write(ByteBuf buffer) {
        ByteBufHelper.writeUtf8(buffer, this.serverId, 32767);
        buffer.writeLong(this.uuid.getMostSignificantBits());
        buffer.writeLong(this.uuid.getLeastSignificantBits());
        ByteBufHelper.writeUtf8(buffer, this.name, 64);
        buffer.writeBoolean(this.joined);
    }

    public static void listener(@Nullable Consumer<PlayerPresenceMessage> listener) {
        PlayerPresenceMessage.listener = listener;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    public void handle(@NotNull MessageBroker<ByteBuf> broker) {
        Consumer<PlayerPresenceMessage> listener = PlayerPresenceMessage.listener;
        if (listener != null) {
            listener.accept(this);
        }
    }
}

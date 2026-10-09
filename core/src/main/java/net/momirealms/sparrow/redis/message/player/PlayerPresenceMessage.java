package net.momirealms.sparrow.redis.message.player;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

public record PlayerPresenceMessage(@NotNull String serverId, @NotNull UUID uuid, @NotNull String name, boolean joined) implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "player_presence");
    public static final MessageCodec<FriendlyByteBuf, PlayerPresenceMessage> CODEC = RedisMessage.codec(PlayerPresenceMessage::write, PlayerPresenceMessage::new);
    private static volatile @Nullable Consumer<PlayerPresenceMessage> listener;

    private PlayerPresenceMessage(FriendlyByteBuf buffer) {
        this(ByteBufHelper.readUtf8(buffer, 32767), buffer.readUUID(), ByteBufHelper.readUtf8(buffer, 64), buffer.readBoolean());
    }

    private void write(FriendlyByteBuf buffer) {
        ByteBufHelper.writeUtf8(buffer, this.serverId, 32767);
        buffer.writeUUID(this.uuid);
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
    public void handle(@NotNull MessageBroker<FriendlyByteBuf> broker) {
        Consumer<PlayerPresenceMessage> listener = PlayerPresenceMessage.listener;
        if (listener != null) {
            listener.accept(this);
        }
    }
}
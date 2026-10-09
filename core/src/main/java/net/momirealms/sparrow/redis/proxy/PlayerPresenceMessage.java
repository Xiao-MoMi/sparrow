package net.momirealms.sparrow.redis.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.cluster.PlayerPresence;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.UUID;

public record PlayerPresenceMessage(@NotNull UUID uuid, @Nullable PlayerPresence player) implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "proxy_player_presence");
    public static final MessageCodec<FriendlyByteBuf, PlayerPresenceMessage> CODEC = RedisMessage.codec(PlayerPresenceMessage::write, PlayerPresenceMessage::new);
    private static volatile @Nullable Consumer<PlayerPresenceMessage> listener;

    private PlayerPresenceMessage(FriendlyByteBuf buffer) {
        this(new UUID(buffer.readLong(), buffer.readLong()), buffer);
    }

    private PlayerPresenceMessage(UUID uuid, FriendlyByteBuf buffer) {
        this(uuid, buffer.readBoolean() ? PlayerPresence.read(uuid, buffer) : null);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeLong(this.uuid.getMostSignificantBits()).writeLong(this.uuid.getLeastSignificantBits());
        buffer.writeBoolean(this.player != null);
        if (this.player != null) {
            this.player.write(buffer);
        }
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
        Consumer<PlayerPresenceMessage> receiver = listener;
        if (receiver != null) {
            receiver.accept(this);
        }
    }
}
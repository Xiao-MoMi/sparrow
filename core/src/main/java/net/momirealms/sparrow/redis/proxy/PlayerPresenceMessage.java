package net.momirealms.sparrow.redis.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.cluster.PlayerDirectory;
import net.momirealms.sparrow.cluster.PlayerPresence;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class PlayerPresenceMessage implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "proxy_player_presence");
    public static final MessageCodec<FriendlyByteBuf, PlayerPresenceMessage> CODEC = RedisMessage.codec(PlayerPresenceMessage::write, PlayerPresenceMessage::new);

    private final PlayerDirectory directory = SparrowPlugin.instance().playerDirectory();
    private final UUID uuid;
    private final @Nullable PlayerPresence player;

    public PlayerPresenceMessage(@NotNull UUID uuid, @Nullable PlayerPresence player) {
        this.uuid = uuid;
        this.player = player;
    }

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

    @NotNull
    public UUID uuid() {
        return this.uuid;
    }

    @Nullable
    public PlayerPresence player() {
        return this.player;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    public void handle(@NotNull MessageBroker<FriendlyByteBuf> broker) {
        this.directory.accept(this);
    }
}
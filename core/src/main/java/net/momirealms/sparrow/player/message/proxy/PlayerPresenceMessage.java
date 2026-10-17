package net.momirealms.sparrow.player.message.proxy;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.PlayerCache;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public final class PlayerPresenceMessage implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "proxy_player_presence");
    public static final MessageCodec<FriendlyByteBuf, PlayerPresenceMessage> CODEC = RedisMessage.codec(PlayerPresenceMessage::write, PlayerPresenceMessage::read);

    private final UUID uuid;
    private final @Nullable String name;
    private final @Nullable String serverId;
    private final @Nullable Locale locale;

    public PlayerPresenceMessage(@NotNull UUID uuid, @NotNull String name, @NotNull String serverId, @NotNull Locale locale) {
        this.uuid = uuid;
        this.name = name;
        this.serverId = serverId;
        this.locale = locale;
    }

    public PlayerPresenceMessage(@NotNull UUID uuid) {
        this.uuid = uuid;
        this.name = null;
        this.serverId = null;
        this.locale = null;
    }

    private static PlayerPresenceMessage read(ByteBuf buffer) {
        UUID uuid = new UUID(buffer.readLong(), buffer.readLong());
        return buffer.readBoolean() ? readOnlineRecord(uuid, buffer) : new PlayerPresenceMessage(uuid);
    }

    /** 读取 Redis 名册和消息共用的在线记录, UUID 由 Hash 字段或消息头提供. */
    @NotNull
    public static PlayerPresenceMessage readOnlineRecord(@NotNull UUID uuid, @NotNull ByteBuf buffer) {
        return new PlayerPresenceMessage(
                uuid,
                ByteBufHelper.readUtf8(buffer, 64),
                ByteBufHelper.readUtf8(buffer, 32767),
                Locale.forLanguageTag(ByteBufHelper.readUtf8(buffer, 128))
        );
    }

    private void write(ByteBuf buffer) {
        buffer.writeLong(this.uuid.getMostSignificantBits()).writeLong(this.uuid.getLeastSignificantBits());
        buffer.writeBoolean(this.serverId != null);
        if (this.serverId != null) {
            ByteBufHelper.writeUtf8(buffer, this.name, 64);
            ByteBufHelper.writeUtf8(buffer, this.serverId, 32767);
            ByteBufHelper.writeUtf8(buffer, this.locale.toLanguageTag(), 128);
        }
    }

    @NotNull
    public UUID uuid() {
        return this.uuid;
    }

    @Nullable
    public String name() {
        return this.name;
    }

    @Nullable
    public String serverId() {
        return this.serverId;
    }

    @Nullable
    public Locale locale() {
        return this.locale;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    public void handle(@NotNull MessageBroker<FriendlyByteBuf> broker) {
        PlayerCache cache = SparrowPlugin.instance().playerManager().playerCache;
        if (this.serverId == null) {
            cache.removePlayer(this);
        } else {
            cache.updatePlayer(this);
        }
    }
}
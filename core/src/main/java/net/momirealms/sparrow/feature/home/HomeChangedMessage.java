package net.momirealms.sparrow.feature.home;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class HomeChangedMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "home_changed");
    public static final MessageCodec<FriendlyByteBuf, HomeChangedMessage> CODEC = RedisMessage.codec(HomeChangedMessage::write, HomeChangedMessage::new);

    private final HomeFeature feature = SparrowPlugin.instance().featureManager().feature(HomeFeature.ID, HomeFeature.class);
    private final String origin;
    private final @Nullable UUID owner; // null 表示所有在线所有者的快照失效.

    private HomeChangedMessage(@NotNull String origin, @Nullable UUID owner) {
        this.origin = origin;
        this.owner = owner;
    }

    @NotNull
    public static HomeChangedMessage invalidateOwner(@NotNull String origin, @NotNull UUID owner) {
        return new HomeChangedMessage(origin, owner);
    }

    @NotNull
    public static HomeChangedMessage invalidateAll(@NotNull String origin) {
        return new HomeChangedMessage(origin, null);
    }

    private HomeChangedMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.origin = ByteBufHelper.readUtf8(buffer, 255);
        this.owner = buffer.readBoolean() ? buffer.readUUID() : null;
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.origin, 255);
        buffer.writeBoolean(this.owner != null);
        if (this.owner != null) {
            buffer.writeUUID(this.owner);
        }
    }

    @NotNull
    public String origin() {
        return this.origin;
    }

    @Nullable
    public UUID owner() {
        return this.owner;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        this.feature.accept(this);
    }
}
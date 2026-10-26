package net.momirealms.sparrow.feature.warp;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class WarpMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "warp_changed");
    public static final MessageCodec<FriendlyByteBuf, WarpMessage> CODEC = RedisMessage.codec(WarpMessage::write, WarpMessage::new);

    private final String origin;
    private final UUID warpId;

    public WarpMessage(@NotNull String origin, @NotNull UUID warpId) {
        this.origin = origin;
        this.warpId = warpId;
    }

    private WarpMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.origin = ByteBufHelper.readUtf8(buffer, 255);
        this.warpId = buffer.readUUID();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.origin, 255);
        buffer.writeUUID(this.warpId);
    }

    @NotNull
    public String origin() {
        return this.origin;
    }

    @NotNull
    public UUID warpId() {
        return this.warpId;
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin.instance().featureManager().feature(WarpFeature.ID, WarpFeature.class).accept(this);
    }
}
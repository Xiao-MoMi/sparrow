package net.momirealms.sparrow.feature.warp;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

public final class WarpMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "warp");
    public static final MessageCodec<FriendlyByteBuf, WarpMessage> CODEC = RedisMessage.codec(WarpMessage::write, WarpMessage::new);

    private final WarpFeature feature = SparrowPlugin.instance().featureManager().feature(WarpFeature.ID, WarpFeature.class);
    private final String origin;

    public WarpMessage(@NotNull String origin) {
        this.origin = origin;
    }

    private WarpMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.origin = ByteBufHelper.readUtf8(buffer, 255);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.origin, 255);
    }

    @NotNull
    public String origin() {
        return this.origin;
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
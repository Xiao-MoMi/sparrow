package net.momirealms.sparrow.feature.ban;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

public final class BanMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "ban_changed");
    public static final MessageCodec<FriendlyByteBuf, BanMessage> CODEC = RedisMessage.codec(BanMessage::write, BanMessage::new);

    private final String origin;
    private final BanRecord record;
    private final boolean silent;

    public BanMessage(@NotNull String origin, @NotNull BanRecord record, boolean silent) {
        this.origin = origin;
        this.record = record;
        this.silent = silent;
    }

    private BanMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.origin = ByteBufHelper.readUtf8(buffer, 255);
        this.record = GsonHelper.get().fromJson(ByteBufHelper.readUtf8(buffer, 16384), BanRecord.class);
        this.silent = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.origin, 255);
        ByteBufHelper.writeUtf8(buffer, GsonHelper.get().toJson(this.record), 16384);
        buffer.writeBoolean(this.silent);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        if (this.origin.equals(ServerConfig.serverId())) return;
        SparrowPlugin.instance().featureManager().feature(BanFeature.ID, BanFeature.class).accept(this.record, this.silent);
    }
}
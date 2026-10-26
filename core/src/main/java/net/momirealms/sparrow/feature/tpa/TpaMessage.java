package net.momirealms.sparrow.feature.tpa;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

public final class TpaMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "tpa_event");
    public static final MessageCodec<FriendlyByteBuf, TpaMessage> CODEC = RedisMessage.codec(TpaMessage::write, TpaMessage::new);

    private final Event event;
    private final TpaRequest request;
    private final String server; // 请求被接受时传送要去的服务器, 其余事件为空

    public TpaMessage(@NotNull Event event, @NotNull TpaRequest request, @NotNull String server) {
        this.event = event;
        this.request = request;
        this.server = server;
    }

    private TpaMessage(@NotNull FriendlyByteBuf buffer) {
        super(buffer);
        this.event = Event.values()[buffer.readByte()];
        this.request = TpaRequest.decode(buffer.readByteArray());
        this.server = ByteBufHelper.readUtf8(buffer, 255);
    }

    @Override
    protected void write(@NotNull FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeByte(this.event.ordinal());
        buffer.writeByteArray(this.request.encode());
        ByteBufHelper.writeUtf8(buffer, this.server, 255);
    }

    @NotNull
    public Event event() {
        return this.event;
    }

    @NotNull
    public TpaRequest request() {
        return this.request;
    }

    @NotNull
    public String server() {
        return this.server;
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        TpaFeature feature = SparrowPlugin.instance().featureManager().feature(TpaFeature.ID, TpaFeature.class);
        if (feature.enabled()) feature.service().handle(this);
    }

    // 序号会写进消息, 新增的事件只能加在末尾
    public enum Event {
        REQUESTED, ACCEPTED, DENIED, CANCELLED, EXPIRED
    }
}
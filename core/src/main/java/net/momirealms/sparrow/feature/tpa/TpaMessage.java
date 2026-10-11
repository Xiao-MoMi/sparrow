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
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "tpa");
    public static final MessageCodec<FriendlyByteBuf, TpaMessage> CODEC = RedisMessage.codec(TpaMessage::write, TpaMessage::new);

    private final TpaFeature feature = SparrowPlugin.instance().featureManager().feature(TpaFeature.ID, TpaFeature.class);
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
        this.request = new TpaRequest(
                buffer.readUUID(),
                ByteBufHelper.readUtf8(buffer, 64),
                buffer.readUUID(),
                ByteBufHelper.readUtf8(buffer, 64),
                TpaRequest.Direction.values()[buffer.readByte()],
                buffer.readLong()
        );
        this.server = ByteBufHelper.readUtf8(buffer, 255);
    }

    @Override
    protected void write(@NotNull FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeByte(this.event.ordinal());
        buffer.writeUUID(this.request.requester());
        ByteBufHelper.writeUtf8(buffer, this.request.requesterName(), 64);
        buffer.writeUUID(this.request.target());
        ByteBufHelper.writeUtf8(buffer, this.request.targetName(), 64);
        buffer.writeByte(this.request.direction().ordinal());
        buffer.writeLong(this.request.createdAt());
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
        this.feature.accept(this);
    }

    // 序号会写进消息, 新增的事件只能加在末尾
    public enum Event {
        REQUESTED, ACCEPTED, DENIED, CANCELLED, EXPIRED
    }
}
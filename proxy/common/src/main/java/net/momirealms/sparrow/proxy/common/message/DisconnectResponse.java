package net.momirealms.sparrow.proxy.common.message;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.NotNull;

public final class DisconnectResponse extends TwoWayResponseMessage<ByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "disconnect_response");
    public static final MessageCodec<ByteBuf, DisconnectResponse> CODEC = RedisMessage.codec(DisconnectResponse::write, DisconnectResponse::new);

    private final boolean disconnected;

    public DisconnectResponse(boolean disconnected) {
        this.disconnected = disconnected;
    }

    private DisconnectResponse(ByteBuf buffer) {
        super(buffer);
        this.disconnected = buffer.readBoolean();
    }

    @Override
    protected void write(ByteBuf buffer) {
        super.write(buffer);
        buffer.writeBoolean(this.disconnected);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    public boolean disconnected() {
        return this.disconnected;
    }
}
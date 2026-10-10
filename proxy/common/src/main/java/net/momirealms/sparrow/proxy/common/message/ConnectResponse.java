package net.momirealms.sparrow.proxy.common.message;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.proxy.common.player.ConnectResult;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.NotNull;

public final class ConnectResponse extends TwoWayResponseMessage<ByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "connect_response");
    public static final MessageCodec<ByteBuf, ConnectResponse> CODEC = RedisMessage.codec(ConnectResponse::write, ConnectResponse::new);

    private final ConnectResult result;

    public ConnectResponse(@NotNull ConnectResult result) {
        this.result = result;
    }

    private ConnectResponse(ByteBuf buffer) {
        super(buffer);
        this.result = ConnectResult.values()[buffer.readUnsignedByte()];
    }

    @Override
    protected void write(ByteBuf buffer) {
        super.write(buffer);
        buffer.writeByte(this.result.ordinal());
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    public ConnectResult result() {
        return this.result;
    }
}
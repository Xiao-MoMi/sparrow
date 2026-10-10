package net.momirealms.sparrow.redis.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.NotNull;

public final class ConnectResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "connect_response");
    public static final MessageCodec<FriendlyByteBuf, ConnectResponse> CODEC = RedisMessage.codec(ConnectResponse::write, ConnectResponse::new);

    private final ConnectResult result;

    public ConnectResponse(@NotNull ConnectResult result) {
        this.result = result;
    }

    private ConnectResponse(FriendlyByteBuf buffer) {
        super(buffer);
        this.result = ConnectResult.values()[buffer.readUnsignedByte()];
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
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
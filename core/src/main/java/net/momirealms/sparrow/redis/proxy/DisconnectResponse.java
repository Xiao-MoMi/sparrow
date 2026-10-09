package net.momirealms.sparrow.redis.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.NotNull;

public final class DisconnectResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "disconnect_response");
    public static final MessageCodec<FriendlyByteBuf, DisconnectResponse> CODEC = RedisMessage.codec(DisconnectResponse::write, DisconnectResponse::new);

    private final boolean disconnected;

    public DisconnectResponse(boolean disconnected) {
        this.disconnected = disconnected;
    }

    private DisconnectResponse(FriendlyByteBuf buffer) {
        super(buffer);
        this.disconnected = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
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
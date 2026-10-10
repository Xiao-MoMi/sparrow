package net.momirealms.sparrow.redis.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ConnectRequest extends TwoWayRequestMessage<FriendlyByteBuf, ConnectResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "connect_request");
    public static final MessageCodec<FriendlyByteBuf, ConnectRequest> CODEC = RedisMessage.codec(ConnectRequest::write, ConnectRequest::new);

    private final UUID player;
    private final String destination;

    public ConnectRequest(@NotNull UUID player, @NotNull String destination) {
        this.player = player;
        this.destination = destination;
    }

    private ConnectRequest(FriendlyByteBuf buffer) {
        super(buffer);
        this.player = new UUID(buffer.readLong(), buffer.readLong());
        this.destination = ByteBufHelper.readUtf8(buffer, 255);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeLong(this.player.getMostSignificantBits()).writeLong(this.player.getLeastSignificantBits());
        ByteBufHelper.writeUtf8(buffer, this.destination, 255);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    @NotNull
    protected CompletableFuture<ConnectResponse> handleRequest() {
        throw new UnsupportedOperationException("Connect requests are handled by the proxy");
    }
}
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

public final class DisconnectRequest extends TwoWayRequestMessage<FriendlyByteBuf, DisconnectResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "disconnect_request");
    public static final MessageCodec<FriendlyByteBuf, DisconnectRequest> CODEC = RedisMessage.codec(DisconnectRequest::write, DisconnectRequest::new);

    private final UUID player;
    private final String reason;

    public DisconnectRequest(@NotNull UUID player, @NotNull String reason) {
        this.player = player;
        this.reason = reason;
    }

    private DisconnectRequest(FriendlyByteBuf buffer) {
        super(buffer);
        this.player = new UUID(buffer.readLong(), buffer.readLong());
        this.reason = ByteBufHelper.readUtf8(buffer, 262144);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeLong(this.player.getMostSignificantBits()).writeLong(this.player.getLeastSignificantBits());
        ByteBufHelper.writeUtf8(buffer, this.reason, 262144);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    @NotNull
    protected CompletableFuture<DisconnectResponse> handleRequest() {
        throw new UnsupportedOperationException("Disconnect requests are handled by the proxy");
    }
}
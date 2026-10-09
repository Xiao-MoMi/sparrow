package net.momirealms.sparrow.proxy.common.message;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerManager;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DisconnectRequest extends TwoWayRequestMessage<ByteBuf, DisconnectResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "disconnect_request");

    private final ProxyPlayerManager platform;
    private final UUID player;
    private final String reason;

    private DisconnectRequest(ByteBuf buffer, ProxyPlayerManager platform) {
        super(buffer);
        this.platform = platform;
        this.player = new UUID(buffer.readLong(), buffer.readLong());
        this.reason = ByteBufHelper.readUtf8(buffer, 262144);
    }

    @NotNull
    public static MessageCodec<ByteBuf, DisconnectRequest> codec(@NotNull ProxyPlayerManager platform) {
        return RedisMessage.codec(DisconnectRequest::write, buffer -> new DisconnectRequest(buffer, platform));
    }

    @Override
    protected void write(ByteBuf buffer) {
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
        return CompletableFuture.completedFuture(new DisconnectResponse(this.platform.disconnect(this.player, this.reason)));
    }
}
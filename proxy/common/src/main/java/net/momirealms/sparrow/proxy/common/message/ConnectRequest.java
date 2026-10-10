package net.momirealms.sparrow.proxy.common.message;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.proxy.common.SparrowProxy;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerManager;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ConnectRequest extends TwoWayRequestMessage<ByteBuf, ConnectResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "connect_request");
    public static final MessageCodec<ByteBuf, ConnectRequest> CODEC = RedisMessage.codec(ConnectRequest::write, ConnectRequest::new);

    private final ProxyPlayerManager players = SparrowProxy.instance().playerManager();
    private final UUID player;
    private final String destination;

    private ConnectRequest(ByteBuf buffer) {
        super(buffer);
        this.player = new UUID(buffer.readLong(), buffer.readLong());
        this.destination = ByteBufHelper.readUtf8(buffer, 255);
    }

    @Override
    protected void write(ByteBuf buffer) {
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
        return this.players.connect(this.player, this.sourceServer(), this.destination).thenApply(ConnectResponse::new);
    }
}
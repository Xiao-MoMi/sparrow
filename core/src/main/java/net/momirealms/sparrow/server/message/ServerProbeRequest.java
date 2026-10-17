package net.momirealms.sparrow.server.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class ServerProbeRequest extends TwoWayRequestMessage<FriendlyByteBuf, ServerProbeResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "server_probe_request");
    public static final MessageCodec<FriendlyByteBuf, ServerProbeRequest> CODEC = RedisMessage.codec(ServerProbeRequest::write, ServerProbeRequest::new);

    private final byte[] expectedInstanceId; // Redis 中观察到的实例 UUID, 只读

    public ServerProbeRequest(@NotNull byte[] expectedInstanceId) {
        this.expectedInstanceId = expectedInstanceId;
    }

    private ServerProbeRequest(@NotNull FriendlyByteBuf buffer) {
        super(buffer);
        this.expectedInstanceId = buffer.readByteArray();
    }

    @Override
    protected void write(@NotNull FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeByteArray(this.expectedInstanceId);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    @Override
    protected CompletableFuture<ServerProbeResponse> handleRequest() {
        return CompletableFuture.completedFuture(
                Arrays.equals(SparrowPlugin.instance().serverManager().registry.instanceId, expectedInstanceId)
                        ? new ServerProbeResponse()
                        : null
        );
    }
}
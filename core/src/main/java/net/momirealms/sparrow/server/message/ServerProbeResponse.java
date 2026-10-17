package net.momirealms.sparrow.server.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ServerProbeResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "server_probe_response");
    public static final MessageCodec<FriendlyByteBuf, ServerProbeResponse> CODEC = RedisMessage.codec(ServerProbeResponse::write, ServerProbeResponse::new);

    public ServerProbeResponse() {
    }

    private ServerProbeResponse(@NotNull FriendlyByteBuf buffer) {
        super(buffer);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }
}
package net.momirealms.sparrow.redis.heartbeat;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

public final class ServerProbeResponseMessage extends TwoWayResponseMessage<ByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "server_probe_response");
    public static final MessageCodec<ByteBuf, ServerProbeResponseMessage> CODEC = RedisMessage.codec(ServerProbeResponseMessage::write, ServerProbeResponseMessage::new);

    private final String token;

    ServerProbeResponseMessage(@NotNull String token) {
        this.token = token;
    }

    private ServerProbeResponseMessage(ByteBuf buf) {
        super(buf);
        this.token = ByteBufHelper.readUtf8(buf, 128);
    }

    @Override
    protected void write(ByteBuf buf) {
        super.write(buf);
        ByteBufHelper.writeUtf8(buf, this.token, 128);
    }

    @NotNull
    public String token() {
        return this.token;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }
}

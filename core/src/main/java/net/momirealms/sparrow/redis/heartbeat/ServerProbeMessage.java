package net.momirealms.sparrow.redis.heartbeat;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class ServerProbeMessage extends TwoWayRequestMessage<ByteBuf, ServerProbeResponseMessage> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "server_probe");
    public static final MessageCodec<ByteBuf, ServerProbeMessage> CODEC = RedisMessage.codec(ServerProbeMessage::write, ServerProbeMessage::new);
    private static volatile ServerHeartBeats registry;

    private final String token;

    ServerProbeMessage(@NotNull String token) {
        this.token = token;
    }

    private ServerProbeMessage(ByteBuf buf) {
        super(buf);
        this.token = ByteBufHelper.readUtf8(buf, 128);
    }

    @Override
    protected void write(ByteBuf buf) {
        super.write(buf);
        ByteBufHelper.writeUtf8(buf, this.token, 128);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    @NotNull
    protected CompletableFuture<ServerProbeResponseMessage> handleRequest() {
        // 本服的身份注册表尚未装配时不应答: 此刻还没占用任何身份, 沉默即"这个 id 无人存活".
        ServerHeartBeats registry = ServerProbeMessage.registry;
        return CompletableFuture.completedFuture(registry == null ? null : registry.answerProbe(this.token));
    }

    static void registry(@NotNull ServerHeartBeats registry) {
        ServerProbeMessage.registry = registry;
    }
}

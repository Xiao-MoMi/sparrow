package net.momirealms.sparrow.proxy.common.message;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.proxy.common.SparrowProxy;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerManager;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class DisconnectMessage extends OneWayMessage<ByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "disconnect");
    public static final MessageCodec<ByteBuf, DisconnectMessage> CODEC = RedisMessage.codec(DisconnectMessage::write, DisconnectMessage::new);

    private final ProxyPlayerManager platform = SparrowProxy.instance().playerManager();
    private final UUID player;
    private final String reason;    // Json 格式的组件

    private DisconnectMessage(ByteBuf buffer) {
        super(buffer);
        this.player = new UUID(buffer.readLong(), buffer.readLong());
        this.reason = ByteBufHelper.readUtf8(buffer, 262144);
    }

    @Override
    protected void write(ByteBuf buffer) {
        super.write(buffer);
        buffer.writeLong(this.player.getMostSignificantBits());
        buffer.writeLong(this.player.getLeastSignificantBits());
        ByteBufHelper.writeUtf8(buffer, this.reason, 262144);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        this.platform.disconnect(this.player, this.reason);
    }
}
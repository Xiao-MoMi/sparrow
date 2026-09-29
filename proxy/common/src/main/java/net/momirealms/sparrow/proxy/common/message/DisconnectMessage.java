package net.momirealms.sparrow.proxy.common.message;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.proxy.common.ProxyPlatform;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class DisconnectMessage extends OneWayMessage<ByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "disconnect");

    private final ProxyPlatform platform;
    private final UUID player;
    private final String reason;    // Json 格式的组件

    private DisconnectMessage(ByteBuf buffer, ProxyPlatform platform) {
        super(buffer);
        this.platform = platform;
        this.player = new UUID(buffer.readLong(), buffer.readLong());
        this.reason = ByteBufHelper.readUtf8(buffer, 262144);
    }

    // 解码出的消息交给当前代理平台处理
    @NotNull
    public static MessageCodec<ByteBuf, DisconnectMessage> codec(@NotNull ProxyPlatform platform) {
        return RedisMessage.codec(DisconnectMessage::write, buffer -> new DisconnectMessage(buffer, platform));
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

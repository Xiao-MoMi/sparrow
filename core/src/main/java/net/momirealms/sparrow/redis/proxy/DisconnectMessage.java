package net.momirealms.sparrow.redis.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class DisconnectMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "disconnect");
    public static final MessageCodec<FriendlyByteBuf, DisconnectMessage> CODEC = RedisMessage.codec(DisconnectMessage::write, DisconnectMessage::new);

    private final UUID player;
    private final String reason;    // Json 格式的组件

    public DisconnectMessage(@NotNull UUID player, @NotNull String reason) {
        this.player = player;
        this.reason = reason;
    }

    private DisconnectMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.player = buffer.readUUID();
        this.reason = ByteBufHelper.readUtf8(buffer, 262144);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.player);
        ByteBufHelper.writeUtf8(buffer, this.reason, 262144);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    // 断开连接由代理端执行.
    @Override
    protected void handle() {
    }
}

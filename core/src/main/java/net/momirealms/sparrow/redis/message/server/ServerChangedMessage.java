package net.momirealms.sparrow.redis.message.server;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class ServerChangedMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "server_changed");
    public static final MessageCodec<FriendlyByteBuf, ServerChangedMessage> CODEC = RedisMessage.codec(ServerChangedMessage::write, ServerChangedMessage::new);
    private static volatile @Nullable Consumer<String> listener;

    private final String serverId;

    public ServerChangedMessage(@NotNull String serverId) {
        this.serverId = serverId;
    }

    private ServerChangedMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.serverId = ByteBufHelper.readUtf8(buffer, 255);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.serverId, 255);
    }

    public static void listener(@Nullable Consumer<String> listener) {
        ServerChangedMessage.listener = listener;
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        Consumer<String> listener = ServerChangedMessage.listener;
        if (listener != null) {
            listener.accept(this.serverId);
        }
    }
}
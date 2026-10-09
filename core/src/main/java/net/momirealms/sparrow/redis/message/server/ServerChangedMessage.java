package net.momirealms.sparrow.redis.message.server;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.cluster.ServerDirectory;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

public final class ServerChangedMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "server_changed");
    public static final MessageCodec<FriendlyByteBuf, ServerChangedMessage> CODEC = RedisMessage.codec(ServerChangedMessage::write, ServerChangedMessage::new);

    private final ServerDirectory directory = SparrowPlugin.instance().serverDirectory();
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

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        this.directory.accept(this.serverId);
    }
}

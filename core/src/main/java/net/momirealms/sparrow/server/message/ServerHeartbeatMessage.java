package net.momirealms.sparrow.server.message;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ServerHeartbeatMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "server_heartbeat");
    public static final MessageCodec<FriendlyByteBuf, ServerHeartbeatMessage> CODEC = RedisMessage.codec(ServerHeartbeatMessage::write, ServerHeartbeatMessage::new);

    private final String serverId;
    private final CompoundTag changes; // 只读增量, 空 NBT 也是有效心跳

    public ServerHeartbeatMessage(@NotNull String serverId, @NotNull CompoundTag changes) {
        this.serverId = serverId;
        this.changes = changes;
    }

    private ServerHeartbeatMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.serverId = ByteBufHelper.readUtf8(buffer, 255);
        Tag tag = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer).readNbt(false);
        if (!(tag instanceof CompoundTag changes)) throw new DecoderException("Server heartbeat changes must be a compound tag");
        this.changes = changes;
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.serverId, 255);
        new net.momirealms.sparrow.util.FriendlyByteBuf(buffer).writeNbt(this.changes, false);
    }

    @NotNull
    public String serverId() {
        return this.serverId;
    }

    @NotNull
    public CompoundTag changes() {
        return this.changes;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        if (this.serverId.equals(ServerConfig.serverId())) return;
        SparrowPlugin.instance().serverManager().registry.applyHeartbeat(this);
    }
}
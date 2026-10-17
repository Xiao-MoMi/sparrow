package net.momirealms.sparrow.server.message;

import io.netty.buffer.ByteBuf;
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
import net.momirealms.sparrow.server.ServerRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ServerChangedMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "server_changed");
    public static final MessageCodec<FriendlyByteBuf, ServerChangedMessage> CODEC = RedisMessage.codec(ServerChangedMessage::write, ServerChangedMessage::new);

    private final String serverId;
    private final @Nullable CompoundTag snapshot; // 启动时携带只读完整状态, null 表示明确关闭

    public ServerChangedMessage(@NotNull String serverId, @NotNull CompoundTag snapshot) {
        this.serverId = serverId;
        this.snapshot = snapshot;
    }

    public ServerChangedMessage(@NotNull String serverId) {
        this.serverId = serverId;
        this.snapshot = null;
    }

    private ServerChangedMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.serverId = ByteBufHelper.readUtf8(buffer, 255);
        if (buffer.readBoolean()) {
            Tag tag = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer).readNbt(false);
            if (!(tag instanceof CompoundTag snapshot)) throw new DecoderException("Server snapshot must be a compound tag");
            this.snapshot = snapshot;
        } else {
            this.snapshot = null;
        }
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.serverId, 255);
        buffer.writeBoolean(this.snapshot != null);
        if (this.snapshot != null) {
            new net.momirealms.sparrow.util.FriendlyByteBuf(buffer).writeNbt(this.snapshot, false);
        }
    }

    @NotNull
    public String serverId() {
        return this.serverId;
    }

    @Nullable
    public CompoundTag snapshot() {
        return this.snapshot;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        // 本服缓存已在发布时应用状态, 跳过自身的广播消息.
        if (this.serverId.equals(ServerConfig.serverId())) return;
        ServerRegistry registry = SparrowPlugin.instance().serverManager().registry;
        if (this.snapshot == null) {
            registry.removeServer(this);
        } else {
            registry.updateServer(this);
        }
    }
}
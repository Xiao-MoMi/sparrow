package net.momirealms.sparrow.teleport.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@ApiStatus.Internal
public final class TeleportCancelMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_cancel");
    public static final MessageCodec<FriendlyByteBuf, TeleportCancelMessage> CODEC = RedisMessage.codec(TeleportCancelMessage::write, TeleportCancelMessage::new);

    private final UUID requestId;

    public TeleportCancelMessage(@NotNull UUID requestId) {
        this.requestId = requestId;
    }

    private TeleportCancelMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.requestId = buffer.readUUID();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.requestId);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin.instance().teleportService().cancel(this.requestId);
    }

    @NotNull
    public UUID requestId() {
        return this.requestId;
    }
}
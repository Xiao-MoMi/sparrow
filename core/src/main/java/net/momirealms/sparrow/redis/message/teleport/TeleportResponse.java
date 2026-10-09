package net.momirealms.sparrow.redis.message.teleport;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.NotNull;

public final class TeleportResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_response");
    public static final MessageCodec<FriendlyByteBuf, TeleportResponse> CODEC = RedisMessage.codec(TeleportResponse::write, TeleportResponse::new);

    private final boolean accepted;

    public TeleportResponse(boolean accepted) {
        this.accepted = accepted;
    }

    private TeleportResponse(FriendlyByteBuf buffer) {
        super(buffer);
        this.accepted = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeBoolean(this.accepted);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    public boolean accepted() {
        return this.accepted;
    }
}
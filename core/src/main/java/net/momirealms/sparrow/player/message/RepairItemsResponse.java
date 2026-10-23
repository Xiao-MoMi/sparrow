package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.NotNull;

public final class RepairItemsResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "repair_items_response");
    public static final MessageCodec<FriendlyByteBuf, RepairItemsResponse> CODEC = RedisMessage.codec(RepairItemsResponse::write, RepairItemsResponse::new);

    private final Status status;
    private final int count;
    private final int restored;
    private final int remaining;
    private final int maximum;

    public RepairItemsResponse(@NotNull Status status, int count, int restored, int remaining, int maximum) {
        this.status = status;
        this.count = count;
        this.restored = restored;
        this.remaining = remaining;
        this.maximum = maximum;
    }

    private RepairItemsResponse(FriendlyByteBuf buffer) {
        super(buffer);
        this.status = buffer.readEnum(Status.class);
        this.count = buffer.readInt();
        this.restored = buffer.readInt();
        this.remaining = buffer.readInt();
        this.maximum = buffer.readInt();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeEnum(this.status);
        buffer.writeInt(this.count);
        buffer.writeInt(this.restored);
        buffer.writeInt(this.remaining);
        buffer.writeInt(this.maximum);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    public Status status() {
        return this.status;
    }

    public int count() {
        return this.count;
    }

    public int restored() {
        return this.restored;
    }

    public int remaining() {
        return this.remaining;
    }

    public int maximum() {
        return this.maximum;
    }

    public enum Status {
        SUCCESS, OFFLINE, ITEMLESS, NOT_DAMAGEABLE
    }
}
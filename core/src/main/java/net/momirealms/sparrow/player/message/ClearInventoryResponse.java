package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.NotNull;

public final class ClearInventoryResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "clear_inventory_response");
    public static final MessageCodec<FriendlyByteBuf, ClearInventoryResponse> CODEC = RedisMessage.codec(ClearInventoryResponse::write, ClearInventoryResponse::new);

    private final boolean online;
    private final int count;

    public ClearInventoryResponse(boolean online, int count) {
        this.online = online;
        this.count = count;
    }

    private ClearInventoryResponse(FriendlyByteBuf buffer) {
        super(buffer);
        this.online = buffer.readBoolean();
        this.count = buffer.readInt();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeBoolean(this.online);
        buffer.writeInt(this.count);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    public boolean online() {
        return this.online;
    }

    public int count() {
        return this.count;
    }
}
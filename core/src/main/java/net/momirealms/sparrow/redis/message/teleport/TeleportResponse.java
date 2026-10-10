package net.momirealms.sparrow.redis.message.teleport;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TeleportResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_response");
    public static final MessageCodec<FriendlyByteBuf, TeleportResponse> CODEC = RedisMessage.codec(TeleportResponse::write, TeleportResponse::new);

    private final boolean accepted;
    private final @Nullable Component denial; // 落点处理器拒绝传送时给玩家的提示

    public TeleportResponse(boolean accepted, @Nullable Component denial) {
        this.accepted = accepted;
        this.denial = denial;
    }

    private TeleportResponse(FriendlyByteBuf buffer) {
        super(buffer);
        this.accepted = buffer.readBoolean();
        this.denial = buffer.readBoolean() ? AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, 32767)) : null;
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeBoolean(this.accepted);
        buffer.writeBoolean(this.denial != null);
        if (this.denial != null) {
            ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(this.denial), 32767);
        }
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    public boolean accepted() {
        return this.accepted;
    }

    @Nullable
    public Component denial() {
        return this.denial;
    }
}
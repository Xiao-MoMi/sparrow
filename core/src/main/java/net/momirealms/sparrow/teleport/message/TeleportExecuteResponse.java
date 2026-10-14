package net.momirealms.sparrow.teleport.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@ApiStatus.Internal
public final class TeleportExecuteResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_execute_response");
    public static final MessageCodec<FriendlyByteBuf, TeleportExecuteResponse> CODEC = RedisMessage.codec(TeleportExecuteResponse::write, TeleportExecuteResponse::new);

    private final UUID requestId;
    private final TeleportResult result;

    public TeleportExecuteResponse(@NotNull UUID requestId, @NotNull TeleportResult result) {
        this.requestId = requestId;
        this.result = result;
    }

    private TeleportExecuteResponse(FriendlyByteBuf buffer) {
        super(buffer);
        this.requestId = buffer.readUUID();
        this.result = buffer.readBoolean()
                ? new TeleportResult.Success()
                : new TeleportResult.Fail(AdventureHelper.jsonToComponent(buffer.readUtf()));
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.requestId);
        switch (this.result) {
            case TeleportResult.Success ignored -> buffer.writeBoolean(true);
            case TeleportResult.Fail fail -> {
                buffer.writeBoolean(false);
                buffer.writeUtf(AdventureHelper.componentToJson(fail.reason()));
            }
        }
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    public UUID requestId() {
        return this.requestId;
    }

    @NotNull
    public TeleportResult result() {
        return this.result;
    }
}
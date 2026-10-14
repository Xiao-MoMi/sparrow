package net.momirealms.sparrow.teleport.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.teleport.TeleportResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@ApiStatus.Internal
public final class TeleportExecuteRequest extends TwoWayRequestMessage<FriendlyByteBuf, TeleportExecuteResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_execute_request");
    public static final MessageCodec<FriendlyByteBuf, TeleportExecuteRequest> CODEC = RedisMessage.codec(TeleportExecuteRequest::write, TeleportExecuteRequest::new);

    private final UUID requestId;

    public TeleportExecuteRequest(@NotNull UUID requestId) {
        this.requestId = requestId;
    }

    private TeleportExecuteRequest(FriendlyByteBuf buffer) {
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

    @NotNull
    @Override
    protected CompletableFuture<TeleportExecuteResponse> handleRequest() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        return plugin.teleportService().execute(this.requestId, this.sourceServer()).handle((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                plugin.logger().warn("Failed to execute teleport " + this.requestId + " from " + this.sourceServer(), cause);
                return new TeleportExecuteResponse(this.requestId, new TeleportResult.Fail(MessageConstants.TELEPORT_FAILED));
            }
            return new TeleportExecuteResponse(this.requestId, result);
        });
    }

    @NotNull
    public UUID requestId() {
        return this.requestId;
    }
}

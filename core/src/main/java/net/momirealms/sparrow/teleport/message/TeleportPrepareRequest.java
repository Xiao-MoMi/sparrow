package net.momirealms.sparrow.teleport.message;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.ListTag;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@ApiStatus.Internal
public final class TeleportPrepareRequest extends TwoWayRequestMessage<FriendlyByteBuf, TeleportPrepareResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_prepare_request");
    public static final MessageCodec<FriendlyByteBuf, TeleportPrepareRequest> CODEC = RedisMessage.codec(TeleportPrepareRequest::write, TeleportPrepareRequest::new);

    private final UUID requestId;
    private final TeleportContext context;
    private final TeleportPosition position;
    private final List<PositionProcessor> targetProcessors;
    private final List<PostProcessor> postProcessors;

    public TeleportPrepareRequest(
            @NotNull UUID requestId,
            @NotNull TeleportContext context,
            @NotNull TeleportPosition position,
            @NotNull List<PositionProcessor> targetProcessors,
            @NotNull List<PostProcessor> postProcessors
    ) {
        this.requestId = requestId;
        this.context = context;
        this.position = position;
        this.targetProcessors = List.copyOf(targetProcessors);
        this.postProcessors = List.copyOf(postProcessors);
    }

    private TeleportPrepareRequest(FriendlyByteBuf buffer) {
        super(buffer);
        this.requestId = buffer.readUUID();
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        Tag context = data.readNbt(false);
        Tag position = data.readNbt(false);
        Tag targets = data.readNbt(false);
        Tag posts = data.readNbt(false);
        if (!(context instanceof CompoundTag contextTag) || !(position instanceof CompoundTag positionTag)
                || !(targets instanceof ListTag targetTags) || !(posts instanceof ListTag postTags)) {
            throw new DecoderException("Teleport preparation requires context, position and two processor lists");
        }
        this.context = TeleportContext.fromNbt(contextTag);
        this.position = TeleportPosition.TYPES.decode(positionTag);
        this.targetProcessors = PositionProcessor.TYPES.decodeList(targetTags);
        this.postProcessors = PostProcessor.TYPES.decodeList(postTags);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.requestId);
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        data.writeNbt(this.context.toNbt(), false);
        data.writeNbt(TeleportPosition.TYPES.encode(this.position), false);
        data.writeNbt(PositionProcessor.TYPES.encodeList(this.targetProcessors), false);
        data.writeNbt(PostProcessor.TYPES.encodeList(this.postProcessors), false);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    @Override
    protected CompletableFuture<TeleportPrepareResponse> handleRequest() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        return plugin.teleportService().prepare(this).handle((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                plugin.logger().warn("Failed to prepare teleport " + this.requestId + " from " + this.sourceServer(), cause);
                return new TeleportPrepareResponse(this.requestId, MessageConstants.TELEPORT_FAILED);
            }
            return switch (result) {
                case TeleportPosition.Result.Success success -> new TeleportPrepareResponse(this.requestId, success.location(), this.context);
                case TeleportPosition.Result.Fail fail -> new TeleportPrepareResponse(this.requestId, fail.reason());
            };
        });
    }

    @NotNull
    public UUID requestId() {
        return this.requestId;
    }

    @NotNull
    public TeleportContext context() {
        return this.context;
    }

    @NotNull
    public TeleportPosition position() {
        return this.position;
    }

    @NotNull
    public List<PositionProcessor> targetProcessors() {
        return this.targetProcessors;
    }

    @NotNull
    public List<PostProcessor> postProcessors() {
        return this.postProcessors;
    }
}

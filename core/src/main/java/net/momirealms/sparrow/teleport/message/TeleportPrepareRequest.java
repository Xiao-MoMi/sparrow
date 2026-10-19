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

    private final UUID requestId;                            // 传送请求 ID
    private final TeleportContext context;                   // 传送上下文
    private final TeleportPosition position;                 // 要解析的目的地
    private final List<PositionProcessor> targetProcessors;  // 按顺序执行的目标处理器

    public TeleportPrepareRequest(
            @NotNull UUID requestId,
            @NotNull TeleportContext context,
            @NotNull TeleportPosition position,
            @NotNull List<PositionProcessor> targetProcessors
    ) {
        this.requestId = requestId;
        this.context = context;
        this.position = position;
        this.targetProcessors = List.copyOf(targetProcessors);
    }

    private TeleportPrepareRequest(FriendlyByteBuf buffer) {
        super(buffer);
        this.requestId = buffer.readUUID();
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        Tag context = data.readNbt(false);
        Tag position = data.readNbt(false);
        Tag targets = data.readNbt(false);
        if (!(context instanceof CompoundTag contextTag) || !(position instanceof CompoundTag positionTag) || !(targets instanceof ListTag targetTags)) {
            throw new DecoderException("Teleport preparation requires context, position and target processors");
        }
        this.context = TeleportContext.fromNbt(contextTag);
        this.position = TeleportPosition.TYPES.decode(positionTag);
        this.targetProcessors = PositionProcessor.TYPES.decodeList(targetTags);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.requestId);
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        data.writeNbt(this.context.toNbt(), false);
        data.writeNbt(TeleportPosition.TYPES.encode(this.position), false);
        data.writeNbt(PositionProcessor.TYPES.encodeList(this.targetProcessors), false);
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

    /**
     * 目标服收到请求时调用, 交给 TeleportService#prepare 处理.
     */
    @NotNull
    @Override
    protected CompletableFuture<TeleportPrepareResponse> handleRequest() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        return plugin.teleportService().acceptTeleportPrepare(this).handle((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                plugin.logger().warn("Failed to prepare teleport " + this.requestId + " from " + this.sourceServer(), cause);
                return new TeleportPrepareResponse(this.requestId, MessageConstants.TELEPORT_FAILED);
            }
            // 成功时把上下文一起带回, 准备阶段写进上下文的内容随之回到源服
            return switch (result) {
                case TeleportPosition.Result.Success success -> new TeleportPrepareResponse(this.requestId, success.location(), this.context);
                case TeleportPosition.Result.Fail fail -> new TeleportPrepareResponse(this.requestId, fail.reason());
            };
        });
    }
}

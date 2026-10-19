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
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class TeleportExecuteRequest extends TwoWayRequestMessage<FriendlyByteBuf, TeleportExecuteResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_execute_request");
    public static final MessageCodec<FriendlyByteBuf, TeleportExecuteRequest> CODEC = RedisMessage.codec(TeleportExecuteRequest::write, TeleportExecuteRequest::new);

    private final UUID requestId;                     // 传送请求 ID
    private final TeleportContext context;            // 准备阶段带回的上下文
    private final WorldLocation location;             // 准备阶段算好的目的地坐标
    private final List<PostProcessor> postProcessors; // 玩家到达后在目标服执行的后处理器

    public TeleportExecuteRequest(UUID requestId, TeleportContext context, WorldLocation location, List<PostProcessor> postProcessors) {
        this.requestId = requestId;
        this.context = context;
        this.location = location;
        this.postProcessors = List.copyOf(postProcessors);
    }

    private TeleportExecuteRequest(FriendlyByteBuf buffer) {
        super(buffer);
        this.requestId = buffer.readUUID();
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        Tag context = data.readNbt(false);
        Tag location = data.readNbt(false);
        Tag posts = data.readNbt(false);
        if (!(context instanceof CompoundTag contextTag) || !(location instanceof CompoundTag locationTag) || !(posts instanceof ListTag postTags)) {
            throw new DecoderException("Teleport execution requires context, location and post processors");
        }
        this.context = TeleportContext.fromNbt(contextTag);
        this.location = WorldLocation.fromNbt(locationTag);
        this.postProcessors = PostProcessor.TYPES.decodeList(postTags);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.requestId);
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        data.writeNbt(this.context.toNbt(), false);
        data.writeNbt(this.location.toNbt(), false);
        data.writeNbt(PostProcessor.TYPES.encodeList(this.postProcessors), false);
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
    public WorldLocation location() {
        return this.location;
    }

    @NotNull
    public List<PostProcessor> postProcessors() {
        return this.postProcessors;
    }

    @NotNull
    @Override
    protected CompletableFuture<TeleportExecuteResponse> handleRequest() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        return plugin.teleportService().execute(this).handle((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                plugin.logger().warn("Failed to execute teleport " + this.requestId + " from " + this.sourceServer(), cause);
                return new TeleportExecuteResponse(this.requestId, new TeleportResult.Fail(MessageConstants.TELEPORT_FAILED));
            }
            return new TeleportExecuteResponse(this.requestId, result);
        });
    }
}

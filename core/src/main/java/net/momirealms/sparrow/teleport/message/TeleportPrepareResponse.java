package net.momirealms.sparrow.teleport.message;

import io.netty.handler.codec.DecoderException;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@ApiStatus.Internal
public final class TeleportPrepareResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_prepare_response");
    public static final MessageCodec<FriendlyByteBuf, TeleportPrepareResponse> CODEC = RedisMessage.codec(TeleportPrepareResponse::write, TeleportPrepareResponse::new);

    private final UUID requestId;                    // 传送请求 ID
    private final boolean successful;                // 准备是否成功, 决定后面三个字段哪些有值
    private final @Nullable WorldLocation location;  // 目的地坐标, 失败时为 null
    private final @Nullable TeleportContext context; // 目标服处理过的上下文, 失败时为 null
    private final @Nullable Component reason;        // 失败原因, 成功时为 null

    public TeleportPrepareResponse(@NotNull UUID requestId, @NotNull WorldLocation location, @NotNull TeleportContext context) {
        this.requestId = requestId;
        this.successful = true;
        this.location = location;
        this.context = context;
        this.reason = null;
    }

    public TeleportPrepareResponse(@NotNull UUID requestId, @NotNull Component reason) {
        this.requestId = requestId;
        this.successful = false;
        this.location = null;
        this.context = null;
        this.reason = reason;
    }

    private TeleportPrepareResponse(FriendlyByteBuf buffer) {
        super(buffer);
        this.requestId = buffer.readUUID();
        this.successful = buffer.readBoolean();
        if (this.successful) {
            net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
            Tag location = data.readNbt(false);
            Tag context = data.readNbt(false);
            if (!(location instanceof CompoundTag locationTag) || !(context instanceof CompoundTag contextTag)) {
                throw new DecoderException("Teleport preparation success requires location and context");
            }
            this.location = WorldLocation.fromNbt(locationTag);
            this.context = TeleportContext.fromNbt(contextTag);
            this.reason = null;
        } else {
            this.location = null;
            this.context = null;
            this.reason = AdventureHelper.jsonToComponent(buffer.readUtf());
        }
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.requestId);
        buffer.writeBoolean(this.successful);
        if (this.successful) {
            net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
            data.writeNbt(this.location.toNbt(), false);
            data.writeNbt(this.context.toNbt(), false);
        } else {
            buffer.writeUtf(AdventureHelper.componentToJson(this.reason));
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

    /**
     * 返回准备是否成功.
     * 成功时 {@link #location()} 和 {@link #context()} 有值, 失败时 {@link #reason()} 有值.
     *
     * @return 准备是否成功
     */
    public boolean successful() {
        return this.successful;
    }

    @Nullable
    public WorldLocation location() {
        return this.location;
    }

    @Nullable
    public TeleportContext context() {
        return this.context;
    }

    @Nullable
    public Component reason() {
        return this.reason;
    }
}
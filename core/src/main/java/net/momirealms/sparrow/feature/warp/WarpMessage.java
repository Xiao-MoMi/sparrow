package net.momirealms.sparrow.feature.warp;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

public final class WarpMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "warp");
    public static final MessageCodec<FriendlyByteBuf, WarpMessage> CODEC = RedisMessage.codec(WarpMessage::write, WarpMessage::new);
    private static volatile @Nullable Consumer<WarpMessage> listener; // 模块启用期间由 WarpRegistry 处理

    private final String origin;            // 发出通知的服务器, 收到自己的通知时忽略
    private final Type type;
    private final @Nullable Warp warp;      // 仅 SAVE 携带
    private final @Nullable UUID id;        // 仅 DELETE 携带

    private WarpMessage(@NotNull String origin, @NotNull Type type, @Nullable Warp warp, @Nullable UUID id) {
        this.origin = origin;
        this.type = type;
        this.warp = warp;
        this.id = id;
    }

    @NotNull
    public static WarpMessage save(@NotNull String origin, @NotNull Warp warp) {
        return new WarpMessage(origin, Type.SAVE, warp, null);
    }

    @NotNull
    public static WarpMessage delete(@NotNull String origin, @NotNull UUID id) {
        return new WarpMessage(origin, Type.DELETE, null, id);
    }

    @NotNull
    public static WarpMessage reload(@NotNull String origin) {
        return new WarpMessage(origin, Type.RELOAD, null, null);
    }

    private WarpMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.origin = ByteBufHelper.readUtf8(buffer, 255);
        this.type = Type.values()[buffer.readByte()];
        this.warp = this.type == Type.SAVE ? readWarp(buffer) : null;
        this.id = this.type == Type.DELETE ? buffer.readUUID() : null;
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.origin, 255);
        buffer.writeByte(this.type.ordinal());
        if (this.warp != null) {
            writeWarp(buffer, this.warp);
        }
        if (this.id != null) {
            buffer.writeUUID(this.id);
        }
    }

    @NotNull
    public String origin() {
        return this.origin;
    }

    @NotNull
    public Type type() {
        return this.type;
    }

    @Nullable
    public Warp warp() {
        return this.warp;
    }

    @Nullable
    public UUID id() {
        return this.id;
    }

    static void listener(@Nullable Consumer<WarpMessage> listener) {
        WarpMessage.listener = listener;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    // 本服未启用 warp 模块时忽略
    @Override
    protected void handle() {
        Consumer<WarpMessage> listener = WarpMessage.listener;
        if (listener != null) {
            listener.accept(this);
        }
    }

    private static void writeWarp(FriendlyByteBuf buffer, Warp warp) {
        WorldLocation location = warp.location();
        buffer.writeUUID(warp.id());
        ByteBufHelper.writeUtf8(buffer, warp.name(), Warp.MAX_NAME_LENGTH);
        ByteBufHelper.writeUtf8(buffer, warp.description(), Warp.MAX_DESCRIPTION_LENGTH);
        ByteBufHelper.writeUtf8(buffer, warp.server(), 255);
        ByteBufHelper.writeUtf8(buffer, location.world(), 255);
        buffer.writeDouble(location.x()).writeDouble(location.y()).writeDouble(location.z());
        buffer.writeFloat(location.yaw()).writeFloat(location.pitch());
        buffer.writeBoolean(warp.creator() != null);
        if (warp.creator() != null) {
            buffer.writeUUID(warp.creator());
        }
        buffer.writeLong(warp.createdAt()).writeLong(warp.updatedAt());
    }

    private static Warp readWarp(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String name = ByteBufHelper.readUtf8(buffer, Warp.MAX_NAME_LENGTH);
        String description = ByteBufHelper.readUtf8(buffer, Warp.MAX_DESCRIPTION_LENGTH);
        String server = ByteBufHelper.readUtf8(buffer, 255);
        WorldLocation location = new WorldLocation(ByteBufHelper.readUtf8(buffer, 255), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat());
        UUID creator = buffer.readBoolean() ? buffer.readUUID() : null;
        return new Warp(id, name, description, server, location, creator, buffer.readLong(), buffer.readLong());
    }

    public enum Type {
        SAVE,   // 新建或修改了一个 warp
        DELETE, // 删除了一个 warp
        RELOAD  // 批量删除后需要整表重读
    }
}

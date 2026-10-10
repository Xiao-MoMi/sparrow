package net.momirealms.sparrow.redis.message.teleport;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportType;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TeleportRequest extends TwoWayRequestMessage<FriendlyByteBuf, TeleportResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_request");
    public static final MessageCodec<FriendlyByteBuf, TeleportRequest> CODEC = RedisMessage.codec(TeleportRequest::write, TeleportRequest::new);

    private final Teleport teleport;

    public TeleportRequest(@NotNull Teleport teleport) {
        this.teleport = teleport;
    }

    private TeleportRequest(FriendlyByteBuf buffer) {
        super(buffer);
        UUID player = buffer.readUUID();
        byte type = buffer.readByte();
        boolean self = buffer.readBoolean();
        WorldLocation location = new WorldLocation(
                ByteBufHelper.readUtf8(buffer, 255),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat()
        );
        this.teleport = new Teleport(player, type < 0 ? null : TeleportType.values()[type], super.targetServer, location, self);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        TeleportType type = this.teleport.type();
        WorldLocation location = this.teleport.location();
        buffer.writeUUID(this.teleport.player());
        buffer.writeByte(type == null ? -1 : type.ordinal());
        buffer.writeBoolean(this.teleport.self());
        ByteBufHelper.writeUtf8(buffer, location.world(), 255);
        buffer.writeDouble(location.x()).writeDouble(location.y()).writeDouble(location.z());
        buffer.writeFloat(location.yaw()).writeFloat(location.pitch());
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    @NotNull
    protected CompletableFuture<TeleportResponse> handleRequest() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        return plugin.teleportService()
                .prepare(this.teleport)
                .whenComplete((response, error) -> {
                    if (error != null) plugin.logger().warn("Failed to prepare the arrival of " + this.teleport.player(), error);
                });
    }
}
package net.momirealms.sparrow.redis.message.teleport;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportDestination;
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

    private static final byte FIXED = 0;     // 落点来源的种类
    private static final byte OF_PLAYER = 1;

    private final Teleport teleport;

    public TeleportRequest(@NotNull Teleport teleport) {
        this.teleport = teleport;
    }

    private TeleportRequest(FriendlyByteBuf buffer) {
        super(buffer);
        UUID player = buffer.readUUID();
        byte type = buffer.readByte();
        boolean self = buffer.readBoolean();
        TeleportDestination destination = switch (buffer.readByte()) {
            case FIXED -> new TeleportDestination.Fixed(new WorldLocation(
                    ByteBufHelper.readUtf8(buffer, 255),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readFloat(),
                    buffer.readFloat()
            ));
            case OF_PLAYER -> new TeleportDestination.OfPlayer(buffer.readUUID());
            default -> throw new IllegalArgumentException("Unknown teleport destination");
        };
        this.teleport = new Teleport(player, type < 0 ? null : TeleportType.values()[type], super.targetServer, destination, self);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        TeleportType type = this.teleport.type();
        buffer.writeUUID(this.teleport.player());
        buffer.writeByte(type == null ? -1 : type.ordinal());
        buffer.writeBoolean(this.teleport.self());
        switch (this.teleport.destination()) {
            case TeleportDestination.Fixed fixed -> {
                WorldLocation location = fixed.location();
                buffer.writeByte(FIXED);
                ByteBufHelper.writeUtf8(buffer, location.world(), 255);
                buffer.writeDouble(location.x()).writeDouble(location.y()).writeDouble(location.z());
                buffer.writeFloat(location.yaw()).writeFloat(location.pitch());
            }
            case TeleportDestination.OfPlayer target -> {
                buffer.writeByte(OF_PLAYER);
                buffer.writeUUID(target.player());
            }
        }
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
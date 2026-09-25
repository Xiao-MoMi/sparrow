package net.momirealms.sparrow.player.teleport;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TeleportRequest extends TwoWayRequestMessage<ByteBuf, TeleportResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_request");
    public static final MessageCodec<ByteBuf, TeleportRequest> CODEC = RedisMessage.codec(TeleportRequest::write, TeleportRequest::new);
    private static volatile TeleportManager manager;

    private final UUID player;
    private final WorldLocation location;

    public TeleportRequest(@NotNull UUID player, @NotNull WorldLocation location) {
        this.player = player;
        this.location = location;
    }

    private TeleportRequest(ByteBuf buffer) {
        super(buffer);
        this.player = new UUID(buffer.readLong(), buffer.readLong());
        this.location = new WorldLocation(ByteBufHelper.readUtf8(buffer, 255), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat());
    }

    @Override
    protected void write(ByteBuf buffer) {
        super.write(buffer);
        buffer.writeLong(this.player.getMostSignificantBits());
        buffer.writeLong(this.player.getLeastSignificantBits());
        ByteBufHelper.writeUtf8(buffer, this.location.world(), 255);
        buffer.writeDouble(this.location.x()).writeDouble(this.location.y()).writeDouble(this.location.z());
        buffer.writeFloat(this.location.yaw()).writeFloat(this.location.pitch());
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    @NotNull
    protected CompletableFuture<TeleportResponse> handleRequest() {
        TeleportManager manager = TeleportRequest.manager;
        return CompletableFuture.completedFuture(new TeleportResponse(manager != null && manager.prepare(this.player, this.location)));
    }

    static void manager(@Nullable TeleportManager manager) {
        TeleportRequest.manager = manager;
    }
}

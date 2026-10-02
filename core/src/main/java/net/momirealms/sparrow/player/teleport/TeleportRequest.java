package net.momirealms.sparrow.player.teleport;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TeleportRequest extends TwoWayRequestMessage<FriendlyByteBuf, TeleportResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "teleport_request");
    public static final MessageCodec<FriendlyByteBuf, TeleportRequest> CODEC = RedisMessage.codec(TeleportRequest::write, TeleportRequest::new);
    private static volatile TeleportManager manager;

    private final UUID player;
    private final WorldLocation location;

    public TeleportRequest(@NotNull UUID player, @NotNull WorldLocation location) {
        this.player = player;
        this.location = location;
    }

    private TeleportRequest(FriendlyByteBuf buffer) {
        super(buffer);
        this.player = buffer.readUUID();
        this.location = new WorldLocation(ByteBufHelper.readUtf8(buffer, 255), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat());
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.player);
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

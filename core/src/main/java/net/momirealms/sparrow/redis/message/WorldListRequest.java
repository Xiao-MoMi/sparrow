package net.momirealms.sparrow.redis.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class WorldListRequest extends TwoWayRequestMessage<FriendlyByteBuf, WorldListResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "world_list_request");
    public static final MessageCodec<FriendlyByteBuf, WorldListRequest> CODEC = RedisMessage.codec(WorldListRequest::write, WorldListRequest::new);

    public WorldListRequest() {
    }

    private WorldListRequest(FriendlyByteBuf buffer) {
        super(buffer);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    @Override
    protected CompletableFuture<WorldListResponse> handleRequest() {
        return CompletableFuture.completedFuture(new WorldListResponse(Bukkit.getWorlds().stream().map(World::getName).toList()));
    }
}
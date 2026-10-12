package net.momirealms.sparrow.redis.message.world;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class WorldListResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "world_list_response");
    public static final MessageCodec<FriendlyByteBuf, WorldListResponse> CODEC = RedisMessage.codec(WorldListResponse::write, WorldListResponse::new);

    private final List<String> worlds;

    public WorldListResponse(@NotNull List<String> worlds) {
        this.worlds = worlds;
    }

    private WorldListResponse(FriendlyByteBuf buffer) {
        super(buffer);
        int size = buffer.readVarInt();
        List<String> worlds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            worlds.add(ByteBufHelper.readUtf8(buffer, 255));
        }
        this.worlds = List.copyOf(worlds);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeVarInt(this.worlds.size());
        for (String world : this.worlds) {
            ByteBufHelper.writeUtf8(buffer, world, 255);
        }
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    public List<String> worlds() {
        return this.worlds;
    }
}
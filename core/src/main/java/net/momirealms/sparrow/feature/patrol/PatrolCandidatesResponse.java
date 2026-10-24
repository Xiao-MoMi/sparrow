package net.momirealms.sparrow.feature.patrol;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayResponseMessage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PatrolCandidatesResponse extends TwoWayResponseMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "patrol_candidates_response");
    public static final MessageCodec<FriendlyByteBuf, PatrolCandidatesResponse> CODEC = RedisMessage.codec(PatrolCandidatesResponse::write, PatrolCandidatesResponse::new);

    private final List<UUID> players;

    PatrolCandidatesResponse(@NotNull List<UUID> players) {
        this.players = List.copyOf(players);
    }

    private PatrolCandidatesResponse(FriendlyByteBuf buffer) {
        super(buffer);
        int size = buffer.readVarInt();
        this.players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.players.add(buffer.readUUID());
        }
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeVarInt(this.players.size());
        for (int i = 0, size = this.players.size(); i < size; i++) {
            buffer.writeUUID(this.players.get(i));
        }
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    List<UUID> players() {
        return this.players;
    }
}
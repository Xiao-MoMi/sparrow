package net.momirealms.sparrow.feature.patrol;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PatrolCandidatesRequest extends TwoWayRequestMessage<FriendlyByteBuf, PatrolCandidatesResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "patrol_candidates_request");
    public static final MessageCodec<FriendlyByteBuf, PatrolCandidatesRequest> CODEC = RedisMessage.codec(PatrolCandidatesRequest::write, PatrolCandidatesRequest::new);

    private final List<UUID> candidates;

    PatrolCandidatesRequest(@NotNull List<UUID> candidates) {
        this.candidates = List.copyOf(candidates);
    }

    private PatrolCandidatesRequest(FriendlyByteBuf buffer) {
        super(buffer);
        int size = buffer.readVarInt();
        this.candidates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.candidates.add(buffer.readUUID());
        }
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeVarInt(this.candidates.size());
        for (int i = 0, size = this.candidates.size(); i < size; i++) {
            buffer.writeUUID(this.candidates.get(i));
        }
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    @Override
    protected CompletableFuture<PatrolCandidatesResponse> handleRequest() {
        return this.execute();
    }

    @NotNull
    CompletableFuture<PatrolCandidatesResponse> execute() {
        PatrolFeature feature = SparrowPlugin.instance().featureManager().feature(PatrolFeature.ID, PatrolFeature.class);
        return feature.filterCandidates(this.candidates).thenApply(PatrolCandidatesResponse::new);
    }
}
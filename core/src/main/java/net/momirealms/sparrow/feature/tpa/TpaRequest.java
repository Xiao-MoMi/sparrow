package net.momirealms.sparrow.feature.tpa;

import net.momirealms.sparrow.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record TpaRequest(
        @NotNull UUID id,
        @NotNull UUID requester,
        @NotNull String requesterName,
        @NotNull UUID target,
        @NotNull String targetName,
        @NotNull Direction direction,
        long createdAt,
        long expiresAt
) {

    byte[] encode() {
        return GsonHelper.get().toJson(this).getBytes(StandardCharsets.UTF_8);
    }

    static TpaRequest decode(byte[] value) {
        return GsonHelper.get().fromJson(new String(value, StandardCharsets.UTF_8), TpaRequest.class);
    }

    public enum Direction {
        TO_TARGET, TO_REQUESTER
    }
}
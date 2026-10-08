package net.momirealms.sparrow.feature.mute;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record MuteRecord(
        @NotNull String id,
        @NotNull UUID player,
        @NotNull String playerName,
        @NotNull String reason,
        @NotNull String operatorName,
        @NotNull String server,
        long createdAt,
        long expiresAt,
        long revokedAt,
        @Nullable String revokedBy
) {
    public static final int MAX_REASON_LENGTH = 256;

    public boolean active(long now) {
        return this.revokedAt == 0 && this.expiresAt > now;
    }

    @NotNull
    public MuteRecord revoke(long now, @NotNull String operator) {
        return new MuteRecord(this.id, this.player, this.playerName, this.reason, this.operatorName, this.server, this.createdAt, this.expiresAt, now, operator);
    }
}
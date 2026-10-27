package net.momirealms.sparrow.feature.ban;

import net.momirealms.sparrow.util.IpRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record BanRecord(
        long id,
        @Nullable UUID player,
        @Nullable String playerName,
        @Nullable IpRange ip,
        @NotNull String reason,
        @NotNull String operatorName,
        @NotNull String server,
        long createdAt,
        long expiresAt,
        long revokedAt,
        @Nullable String revokedBy
) {
    public static final int MAX_REASON_LENGTH = 256;

    public boolean permanent() {
        return this.expiresAt == 0;
    }

    public boolean active(long now) {
        return this.revokedAt == 0 && (this.expiresAt == 0 || this.expiresAt > now);
    }
}
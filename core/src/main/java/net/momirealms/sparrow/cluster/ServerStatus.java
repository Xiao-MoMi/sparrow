package net.momirealms.sparrow.cluster;

import org.jetbrains.annotations.NotNull;

public record ServerStatus(
        @NotNull String serverId,
        long startedAt,
        int onlinePlayers,
        int maxPlayers,
        @NotNull String platform
) {
}

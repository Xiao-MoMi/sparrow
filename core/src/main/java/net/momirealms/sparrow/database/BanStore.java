package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanResult;
import net.momirealms.sparrow.util.IpRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BanStore {

    @NotNull
    CompletableFuture<BanResult> createPlayer(
            @NotNull UUID player,
            @Nullable String playerName,
            @NotNull String reason,
            @NotNull String operator,
            @NotNull String server,
            long createdAt,
            long expiresAt,
            boolean force
    );

    @NotNull
    CompletableFuture<BanResult> createIp(@NotNull IpRange ip, @NotNull String reason, @NotNull String operator, @NotNull String server, long createdAt, long expiresAt, boolean force);

    @NotNull
    CompletableFuture<Optional<BanRecord>> revokePlayer(@NotNull UUID player, long now, @NotNull String revokedBy);

    @NotNull
    CompletableFuture<Optional<BanRecord>> revokeIp(@NotNull IpRange ip, long now, @NotNull String revokedBy);

    @NotNull
    CompletableFuture<Optional<BanRecord>> revokeId(long id, long now, @NotNull String revokedBy);

    @NotNull
    CompletableFuture<Optional<BanRecord>> findActiveBan(@NotNull UUID player, long ip, long now);

    @NotNull
    CompletableFuture<Optional<BanRecord>> find(long id);

    @NotNull
    CompletableFuture<Long> count(@NotNull BanQuery query);

    @NotNull
    CompletableFuture<List<BanRecord>> list(@NotNull BanQuery query, int offset, int limit);
}
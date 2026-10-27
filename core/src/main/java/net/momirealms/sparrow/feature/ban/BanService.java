package net.momirealms.sparrow.feature.ban;

import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.IpRange;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public final class BanService {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final BanStore store = this.plugin.dataStorage().banStore();
    private final BanFeature feature;
    private final Set<CompletableFuture<?>> operations = new HashSet<>();
    private boolean accepting;

    BanService(BanFeature feature) {
        this.feature = feature;
    }

    synchronized void start() {
        this.accepting = true;
    }

    @NotNull
    public CompletableFuture<Result> banPlayer(@NotNull String input, @NotNull String reason, @Nullable Duration time, @NotNull String operator, boolean force, boolean silent) {
        return this.execute(() -> this.resolvePlayer(input).thenCompose(player -> {
            if (player == null) return CompletableFuture.completedFuture(new Result(Status.UNKNOWN_PLAYER, null, null));
            return this.create(reason, time, silent, creation -> this.store.createPlayer(
                    player.uuid(),
                    player.name(),
                    creation.reason(),
                    operator,
                    ServerConfig.serverId(),
                    creation.createdAt(),
                    creation.expiresAt(),
                    force
            ));
        }));
    }

    @NotNull
    public CompletableFuture<Result> banPlayerIp(@NotNull String input, @NotNull String reason, @Nullable Duration time, @NotNull String operator, boolean force, boolean silent) {
        return this.execute(() -> this.withPlayerIp(input, ip -> this.create(reason, time, silent, creation -> this.store.createIp(
                ip,
                creation.reason(),
                operator,
                ServerConfig.serverId(),
                creation.createdAt(),
                creation.expiresAt(),
                force
        ))));
    }

    @NotNull
    public CompletableFuture<Result> banIp(@NotNull String input, @NotNull String reason, @Nullable Duration time, @NotNull String operator, boolean force, boolean silent) {
        return this.execute(() -> this.withIp(input, ip -> this.create(reason, time, silent, creation -> this.store.createIp(
                ip,
                creation.reason(),
                operator,
                ServerConfig.serverId(),
                creation.createdAt(),
                creation.expiresAt(),
                force
        ))));
    }

    private CompletableFuture<Result> create(String input, @Nullable Duration time, boolean silent, Function<Creation, CompletableFuture<BanResult>> action) {
        String reason = this.feature.config().getReason(input);
        if (reason.codePointCount(0, reason.length()) > BanRecord.MAX_REASON_LENGTH) {
            return CompletableFuture.completedFuture(new Result(Status.REASON_TOO_LONG, null, null));
        }
        long now = System.currentTimeMillis();
        long expiresAt = 0;
        if (time != null) {
            try {
                long millis = time.toMillis();
                if (millis <= 0) return CompletableFuture.completedFuture(new Result(Status.INVALID_DURATION, null, null));
                expiresAt = Math.addExact(now, millis);
            } catch (ArithmeticException exception) {
                return CompletableFuture.completedFuture(new Result(Status.INVALID_DURATION, null, null));
            }
        }
        return action.apply(new Creation(reason, now, expiresAt)).thenCompose(result -> {
            Status status = switch (result.status()) {
                case CREATED -> Status.CREATED;
                case REPLACED -> Status.REPLACED;
                case ALREADY_BANNED -> Status.ALREADY_BANNED;
            };
            Result outcome = new Result(status, result.record(), result.previous());
            if (status == Status.ALREADY_BANNED) return CompletableFuture.completedFuture(outcome);
            return this.changed(result.record(), silent).thenApply(ignored -> outcome);
        });
    }

    @NotNull
    public CompletableFuture<Result> unbanPlayer(@NotNull String input, @NotNull String operator, boolean silent) {
        return this.execute(() -> this.resolvePlayer(input).thenCompose(player -> {
            if (player == null) return CompletableFuture.completedFuture(new Result(Status.UNKNOWN_PLAYER, null, null));
            return this.revoked(this.store.revokePlayer(player.uuid(), System.currentTimeMillis(), operator), silent);
        }));
    }

    @NotNull
    public CompletableFuture<Result> unbanPlayerIp(@NotNull String input, @NotNull String operator, boolean silent) {
        return this.execute(() -> this.withPlayerIp(input, ip -> this.revoked(this.store.revokeIp(ip, System.currentTimeMillis(), operator), silent)));
    }

    @NotNull
    public CompletableFuture<Result> unbanIp(@NotNull String input, @NotNull String operator, boolean silent) {
        return this.execute(() -> this.withIp(input, ip -> this.revoked(this.store.revokeIp(ip, System.currentTimeMillis(), operator), silent)));
    }

    @NotNull
    public CompletableFuture<Result> unbanId(long id, @NotNull String operator, boolean silent) {
        return this.execute(() -> this.revoked(this.store.revokeId(id, System.currentTimeMillis(), operator), silent));
    }

    private CompletableFuture<Result> revoked(CompletableFuture<Optional<BanRecord>> operation, boolean silent) {
        return operation.thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.completedFuture(new Result(Status.NOT_FOUND, null, null));
            BanRecord record = found.get();
            return this.changed(record, silent).thenApply(ignored -> new Result(Status.REVOKED, record, null));
        });
    }

    private CompletableFuture<@Nullable PlayerIdentity> resolvePlayer(String input) {
        UUID uuid = UUIDUtils.parse(input);
        if (uuid != null) {
            return this.plugin.playerManager().getPlayer(uuid).thenApply(found -> new PlayerIdentity(uuid, found.map(player -> player.name()).orElse(null)));
        }
        return this.plugin.playerManager().getPlayer(input).thenApply(found -> found.map(player -> new PlayerIdentity(player.uniqueId(), player.name())).orElse(null));
    }

    private CompletableFuture<Result> withPlayerIp(String input, Function<IpRange, CompletableFuture<Result>> action) {
        return this.resolvePlayer(input).thenCompose(player -> {
            if (player == null) return CompletableFuture.completedFuture(new Result(Status.UNKNOWN_PLAYER, null, null));
            return this.plugin.dataStorage().playerIpHistoryStore().findLatestByUuid(player.uuid()).thenCompose(found -> {
                if (found.isEmpty()) return CompletableFuture.completedFuture(new Result(Status.NO_ADDRESS, null, null));
                long address = IpRange.address(found.get().ip());
                if (address == IpRange.NONE) return CompletableFuture.completedFuture(new Result(Status.UNSUPPORTED_ADDRESS, null, null));
                return action.apply(IpRange.of(address));
            });
        });
    }

    private CompletableFuture<Result> withIp(String input, Function<IpRange, CompletableFuture<Result>> action) {
        IpRange range;
        try {
            range = IpRange.parse(input);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(new Result(Status.INVALID_IP, null, null));
        }
        return action.apply(range);
    }

    private CompletableFuture<Void> changed(BanRecord record, boolean silent) {
        return this.plugin.messageBrokerManager().publishOneWay(new BanMessage(ServerConfig.serverId(), record, silent), "")
                .thenRun(() -> this.feature.accept(record, silent));
    }

    @NotNull
    public CompletableFuture<Optional<BanTarget.PlayerTarget>> resolvePlayerTarget(@NotNull String input) {
        return this.execute(() -> this.resolvePlayer(input)
                .thenApply(player -> player == null ? Optional.empty() : Optional.of(new BanTarget.PlayerTarget(player.uuid()))));
    }

    @NotNull
    public CompletableFuture<Optional<BanRecord>> find(long id) {
        return this.execute(() -> this.store.find(id));
    }

    @NotNull
    public CompletableFuture<Long> count(@NotNull BanQuery query) {
        return this.execute(() -> this.store.count(query));
    }

    @NotNull
    public CompletableFuture<List<BanRecord>> list(@NotNull BanQuery query, int offset, int limit) {
        return this.execute(() -> this.store.list(query, offset, limit));
    }

    CompletableFuture<Optional<BanRecord>> findActive(UUID player, long ip) {
        return this.execute(() -> this.store.findActiveBan(player, ip, System.currentTimeMillis()));
    }

    private <T> CompletableFuture<T> execute(Supplier<CompletableFuture<T>> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (this) {
            if (!this.accepting) return CompletableFuture.failedFuture(new CancellationException("Ban service is disabled"));
            this.operations.add(result);
        }
        CompletableFuture.completedFuture(null).thenCompose(ignored -> action.get()).whenComplete((value, error) -> {
            if (error == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(error);
            }
            synchronized (this) {
                this.operations.remove(result);
            }
        });
        return result.copy();
    }

    synchronized CompletableFuture<Void> stop() {
        this.accepting = false;
        // 原调用方接收操作结果, 收尾等待已接受的身份查询, SQL 和消息发布结束.
        return CompletableFuture.allOf(this.operations.toArray(CompletableFuture[]::new)).handle((ignored, error) -> null);
    }

    public enum Status {
        CREATED, REPLACED, ALREADY_BANNED, REVOKED, NOT_FOUND,
        UNKNOWN_PLAYER, NO_ADDRESS, UNSUPPORTED_ADDRESS, INVALID_IP, INVALID_DURATION, REASON_TOO_LONG
    }

    public record Result(@NotNull Status status, @Nullable BanRecord record, @Nullable BanRecord previous) {
    }

    private record Creation(String reason, long createdAt, long expiresAt) {
    }

    private record PlayerIdentity(UUID uuid, @Nullable String name) {
    }
}
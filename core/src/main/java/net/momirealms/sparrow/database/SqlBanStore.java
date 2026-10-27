package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanResult;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.util.IpRange;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public abstract class SqlBanStore implements BanStore {
    private static final String COLUMNS = "id, player, player_name, ip_start, ip_end, reason, operator_name, server, created_at, expires_at, revoked_at, revoked_by";
    private static final String ACTIVE = "revoked_at = 0 AND (expires_at = 0 OR expires_at > :now)";

    private final Jdbi sql;
    private final Executor executor;
    private final String table;

    protected SqlBanStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String table) {
        this.sql = sql;
        this.executor = executor;
        this.table = table;
    }

    @NotNull
    @Override
    public CompletableFuture<BanResult> createPlayer(
            @NotNull UUID player,
            @Nullable String playerName,
            @NotNull String reason,
            @NotNull String operator,
            @NotNull String server,
            long createdAt,
            long expiresAt,
            boolean force
    ) {
        return this.create(new NewBan(new BanTarget.PlayerTarget(player), playerName, reason, operator, server, createdAt, expiresAt), force);
    }

    @NotNull
    @Override
    public CompletableFuture<BanResult> createIp(
            @NotNull IpRange ip,
            @NotNull String reason,
            @NotNull String operator,
            @NotNull String server,
            long createdAt,
            long expiresAt,
            boolean force
    ) {
        return this.create(new NewBan(new BanTarget.IpTarget(ip), null, reason, operator, server, createdAt, expiresAt), force);
    }

    private CompletableFuture<BanResult> create(NewBan request, boolean force) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.sql.inTransaction(handle -> this.create(handle, request, force));
            } catch (UnableToExecuteStatementException exception) {
                // 同一目标并发首次插入由唯一键裁决, 事务已经回滚后才返回业务拒绝.
                if (exception.getCause() instanceof SQLException cause && this.duplicateActiveKey(cause)) {
                    return new BanResult(BanResult.Status.ALREADY_BANNED, null, null);
                }
                throw exception;
            }
        }, this.executor);
    }

    private BanResult create(Handle handle, NewBan request, boolean force) {
        String key = request.target().getActiveKey();
        BanRecord previous = handle.createQuery("SELECT " + COLUMNS + " FROM " + this.table + " WHERE active_key = :key FOR UPDATE")
                .bind("key", key)
                .map((row, context) -> this.read(row))
                .findOne()
                .orElse(null);
        BanRecord replaced = null;
        if (previous != null) {
            if (previous.active(request.createdAt())) {
                if (!force) return new BanResult(BanResult.Status.ALREADY_BANNED, null, previous);
                replaced = this.revoke(handle, previous, request.createdAt(), request.operatorName());
            } else {
                // 自然到期只释放占位, 保留未主动撤销的历史状态.
                handle.createUpdate("UPDATE " + this.table + " SET active_key = NULL WHERE id = :id")
                        .bind("id", previous.id())
                        .execute();
            }
        }
        String insert = "INSERT INTO " + this.table + " (active_key, player, player_name, ip_start, ip_end, reason, operator_name, server, created_at, expires_at)"
                + " VALUES (:active_key, :player, :player_name, :ip_start, :ip_end, :reason, :operator_name, :server, :created_at, :expires_at)";
        UUID player = request.target() instanceof BanTarget.PlayerTarget target ? target.uuid() : null;
        IpRange ip = request.target() instanceof BanTarget.IpTarget target ? target.range() : null;
        long id = handle.createUpdate(insert)
                .bind("active_key", key)
                .bindBySqlType("player", player == null ? null : this.encodeUuid(player), this.uuidSqlType())
                .bindBySqlType("player_name", request.playerName(), Types.VARCHAR)
                .bindBySqlType("ip_start", ip == null ? null : ip.start(), Types.BIGINT)
                .bindBySqlType("ip_end", ip == null ? null : ip.end(), Types.BIGINT)
                .bind("reason", request.reason())
                .bind("operator_name", request.operatorName())
                .bind("server", request.server())
                .bind("created_at", request.createdAt())
                .bind("expires_at", request.expiresAt())
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
        BanRecord created = new BanRecord(id, player, request.playerName(), ip, request.reason(), request.operatorName(), request.server(), request.createdAt(), request.expiresAt(), 0, null);
        return new BanResult(replaced == null ? BanResult.Status.CREATED : BanResult.Status.REPLACED, created, replaced);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<BanRecord>> revokePlayer(@NotNull UUID player, long now, @NotNull String revokedBy) {
        return this.revoke("active_key = :target", new BanTarget.PlayerTarget(player).getActiveKey(), now, revokedBy);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<BanRecord>> revokeIp(@NotNull IpRange ip, long now, @NotNull String revokedBy) {
        return this.revoke("active_key = :target", new BanTarget.IpTarget(ip).getActiveKey(), now, revokedBy);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<BanRecord>> revokeId(long id, long now, @NotNull String revokedBy) {
        return this.revoke("id = :target", id, now, revokedBy);
    }

    private CompletableFuture<Optional<BanRecord>> revoke(String match, Object target, long now, String revokedBy) {
        String select = "SELECT " + COLUMNS + " FROM " + this.table + " WHERE " + match + " AND " + ACTIVE + " FOR UPDATE";
        return CompletableFuture.supplyAsync(() -> this.sql.inTransaction(handle -> handle.createQuery(select)
                .bind("target", target)
                .bind("now", now)
                .map((row, context) -> this.read(row))
                .findOne()
                .map(record -> this.revoke(handle, record, now, revokedBy))), this.executor);
    }

    private BanRecord revoke(Handle handle, BanRecord record, long now, String revokedBy) {
        handle.createUpdate("UPDATE " + this.table + " SET active_key = NULL, revoked_at = :now, revoked_by = :by WHERE id = :id")
                .bind("id", record.id())
                .bind("now", now)
                .bind("by", revokedBy)
                .execute();
        return new BanRecord(
                record.id(),
                record.player(),
                record.playerName(),
                record.ip(),
                record.reason(),
                record.operatorName(),
                record.server(),
                record.createdAt(),
                record.expiresAt(),
                now,
                revokedBy
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<BanRecord>> findActiveBan(@NotNull UUID player, long ip, long now) {
        String select = "SELECT " + COLUMNS + " FROM " + this.table + " WHERE " + ACTIVE
                + " AND (player = :player OR (ip_start <= :ip AND ip_end >= :ip))"
                + " ORDER BY CASE WHEN player = :player THEN 0 ELSE 1 END, CASE WHEN expires_at = 0 THEN 0 ELSE 1 END, expires_at DESC, id DESC LIMIT 1";
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createQuery(select)
                .bind("player", this.encodeUuid(player))
                .bind("ip", ip)
                .bind("now", now)
                .map((row, context) -> this.read(row))
                .findOne()), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<BanRecord>> find(long id) {
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createQuery("SELECT " + COLUMNS + " FROM " + this.table + " WHERE id = :id")
                .bind("id", id)
                .map((row, context) -> this.read(row))
                .findOne()), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Long> count(@NotNull BanQuery query) {
        String select = "SELECT COUNT(*) FROM " + this.table + this.where(query);
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> this.bindQuery(handle.createQuery(select), query)
                .mapTo(Long.class)
                .one()), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<List<BanRecord>> list(@NotNull BanQuery query, int offset, int limit) {
        String select = "SELECT " + COLUMNS + " FROM " + this.table + this.where(query) + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> this.bindQuery(handle.createQuery(select), query)
                .bind("offset", offset)
                .bind("limit", limit)
                .map((row, context) -> this.read(row))
                .list()), this.executor);
    }

    private String where(BanQuery query) {
        List<String> conditions = new ArrayList<>();
        if (query.target() != null) {
            conditions.add(switch (query.target()) {
                case BanTarget.PlayerTarget ignored -> "player = :player";
                case BanTarget.IpTarget ignored -> "ip_start <= :ip_start AND ip_end >= :ip_end";
            });
        }
        if (query.operator() != null) {
            conditions.add("LOWER(operator_name) = LOWER(:operator)");
        }
        if (query.since() > 0) {
            conditions.add("created_at >= :since");
        }
        if (query.activeOnly()) {
            conditions.add(ACTIVE);
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private Query bindQuery(Query statement, BanQuery query) {
        if (query.target() != null) {
            switch (query.target()) {
                case BanTarget.PlayerTarget target -> statement.bind("player", this.encodeUuid(target.uuid()));
                case BanTarget.IpTarget target -> statement.bind("ip_start", target.range().start()).bind("ip_end", target.range().end());
            }
        }
        if (query.operator() != null) {
            statement.bind("operator", query.operator());
        }
        if (query.since() > 0) {
            statement.bind("since", query.since());
        }
        if (query.activeOnly()) {
            statement.bind("now", query.now());
        }
        return statement;
    }

    private BanRecord read(ResultSet row) throws SQLException {
        long start = row.getLong("ip_start");
        IpRange ip = row.wasNull() ? null : new IpRange(start, row.getLong("ip_end"));
        return new BanRecord(
                row.getLong("id"),
                this.readUuid(row, "player"),
                row.getString("player_name"),
                ip,
                row.getString("reason"),
                row.getString("operator_name"),
                row.getString("server"),
                row.getLong("created_at"),
                row.getLong("expires_at"),
                row.getLong("revoked_at"),
                row.getString("revoked_by")
        );
    }

    protected abstract Object encodeUuid(UUID uuid);

    protected abstract int uuidSqlType();

    @Nullable
    protected abstract UUID readUuid(ResultSet row, String column) throws SQLException;

    protected abstract boolean duplicateActiveKey(SQLException exception);

    private record NewBan(BanTarget target, @Nullable String playerName, String reason, String operatorName, String server, long createdAt, long expiresAt) {
    }
}
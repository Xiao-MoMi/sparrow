package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanResult;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.player.PlayerIdentity;
import net.momirealms.sparrow.util.IpRange;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.SqlStatement;
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
import java.util.function.Supplier;

// MySQL 与 PostgreSQL 共用的封禁查询, 子类负责建表和 UUID 列类型.
@ApiStatus.Internal
public abstract class SqlBanStore implements BanStore {
    private static final String ACTIVE = "revoked_at = 0 AND (expires_at = 0 OR expires_at > :now)";

    private final Supplier<Jdbi> jdbi;
    private final Executor executor;
    private final String bans;
    private volatile boolean initialized; // 表结构已在本次运行中确认过

    // 表名需要调用方按方言加好引号
    protected SqlBanStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull String bans) {
        this.jdbi = jdbi;
        this.executor = executor;
        this.bans = bans;
    }

    // 在迁移锁内创建或升级封禁表
    protected abstract void migrate(@NotNull Jdbi jdbi);

    @NotNull
    protected abstract Object uuidValue(@NotNull UUID uuid);

    // 绑定空 UUID 时使用的 JDBC 类型
    protected abstract int uuidNullType();

    @Nullable
    protected abstract UUID readUuid(@NotNull ResultSet result, @NotNull String column) throws SQLException;

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::sql, this.executor);
    }

    // 第一次使用时在数据库线程上准备表结构, 失败后下一次使用会重新尝试
    private Jdbi sql() {
        Jdbi jdbi = this.jdbi.get();
        if (!this.initialized) {
            synchronized (this) {
                if (!this.initialized) {
                    this.migrate(jdbi);
                    this.initialized = true;
                }
            }
        }
        return jdbi;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<BanRecord>> findActiveBan(@NotNull UUID player, long ip, long now) {
        // 封禁该账号的记录优先, 其次永久封禁, 再按到期时间从晚到早
        String sql = "SELECT * FROM " + this.bans + " WHERE " + ACTIVE + " AND (player = :player OR (ip_start <= :ip AND ip_end >= :ip))"
                + " ORDER BY CASE WHEN player = :player THEN 0 ELSE 1 END, CASE WHEN expires_at = 0 THEN 0 ELSE 1 END, expires_at DESC LIMIT 1";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindUuid(handle.createQuery(sql), "player", player)
                .bind("ip", ip)
                .bind("now", now)
                .map((result, context) -> this.readBan(result))
                .findOne()), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<BanResult> saveBan(@NotNull BanRecord record, boolean force) {
        IpRange ip = record.ip();
        // 带玩家的记录覆盖该玩家的旧封禁, 纯 IP 记录覆盖同一段的纯 IP 封禁
        String sameTarget = record.player() != null ? "player = :player" : "player IS NULL AND ip_start = :ip_start AND ip_end = :ip_end";
        String select = "SELECT * FROM " + this.bans + " WHERE " + ACTIVE + " AND " + sameTarget + " FOR UPDATE";
        String revoke = "UPDATE " + this.bans + " SET revoked_at = :now, revoked_by = :by WHERE id IN (<ids>)";
        String insert = "INSERT INTO " + this.bans + " (id, player, player_name, ip_start, ip_end, reason, operator_name, server, created_at, expires_at)"
                + " VALUES (:id, :player, :player_name, :ip_start, :ip_end, :reason, :operator_name, :server, :created_at, :expires_at)";
        return CompletableFuture.supplyAsync(() -> this.sql().inTransaction(handle -> {
            Query selectStatement = handle.createQuery(select).bind("now", record.createdAt());
            if (record.player() != null) {
                this.bindUuid(selectStatement, "player", record.player());
            } else {
                selectStatement.bind("ip_start", ip.start()).bind("ip_end", ip.end());
            }
            // 事务内锁住当前封禁, 只撤销本次检查过的记录
            List<BanRecord> active = selectStatement.map((row, context) -> this.readBan(row)).list();
            BanResult result = BanResult.evaluate(record, active, force);
            if (result.status() == BanResult.Status.REPLACEMENT_REJECTED) {
                return result;
            }
            if (!active.isEmpty()) {
                int size = active.size();
                List<String> ids = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    ids.add(active.get(i).id());
                }
                handle.createUpdate(revoke)
                        .bindList("ids", ids)
                        .bind("now", record.createdAt())
                        .bind("by", record.operatorName())
                        .execute();
            }
            this.bindRecordTarget(handle.createUpdate(insert), record)
                    .bind("id", record.id())
                    .bind("reason", record.reason())
                    .bind("operator_name", record.operatorName())
                    .bind("server", record.server())
                    .bind("created_at", record.createdAt())
                    .bind("expires_at", record.expiresAt())
                    .execute();
            return result;
        }), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<BanRecord>> revokeBans(@NotNull BanTarget target, long now, @NotNull String revokedBy) {
        String match = switch (target) {
            case BanTarget.PlayerTarget ignored -> "player = :player";
            case BanTarget.IpTarget ignored -> "player IS NULL AND ip_start = :ip_start AND ip_end = :ip_end";
            case BanTarget.IdTarget ignored -> "id = :id";
        };
        String select = "SELECT * FROM " + this.bans + " WHERE " + ACTIVE + " AND " + match;
        String revoke = "UPDATE " + this.bans + " SET revoked_at = :now, revoked_by = :by WHERE id IN (<ids>)";
        return CompletableFuture.supplyAsync(() ->
                this.sql().inTransaction(handle -> {
                    List<BanRecord> revoked = this.bindTarget(handle.createQuery(select), target)
                            .bind("now", now)
                            .map((result, context) -> this.readBan(result))
                            .list();
                    if (revoked.isEmpty()) {
                        return revoked;
                    }
                    int size = revoked.size();
                    List<String> ids = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        ids.add(revoked.get(i).id());
                    }
                    handle.createUpdate(revoke).bindList("ids", ids).bind("now", now).bind("by", revokedBy).execute();
                    return revoked;
                }), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Long> countBans(@NotNull BanQuery query) {
        String sql = "SELECT COUNT(*) FROM " + this.bans + where(query);
        return CompletableFuture.supplyAsync(
                () -> this.sql().withHandle(handle -> this.bindQuery(handle.createQuery(sql), query).mapTo(Long.class).one()),
                this.executor
        );
    }

    @Override
    @NotNull
    public CompletableFuture<List<BanRecord>> listBans(@NotNull BanQuery query, int offset, int limit) {
        String sql = "SELECT * FROM " + this.bans + where(query) + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindQuery(handle.createQuery(sql), query)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((result, context) -> this.readBan(result))
                .list()), this.executor);
    }

    // IP 目标匹配完整覆盖它的 IP 段, 包括账号加 IP 的封禁
    private static String where(BanQuery query) {
        List<String> conditions = new ArrayList<>(4);
        BanTarget target = query.target();
        if (target != null) {
            conditions.add(switch (target) {
                case BanTarget.PlayerTarget ignored -> "player = :player";
                case BanTarget.IpTarget ignored -> "ip_start <= :ip_start AND ip_end >= :ip_end";
                case BanTarget.IdTarget ignored -> "id = :id";
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

    // Jdbi 不允许多余的具名参数, 只绑定 where 里实际出现的
    private <S extends SqlStatement<S>> S bindQuery(S statement, BanQuery query) {
        if (query.target() != null) {
            this.bindTarget(statement, query.target());
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

    private <S extends SqlStatement<S>> S bindTarget(S statement, BanTarget target) {
        return switch (target) {
            case BanTarget.PlayerTarget(PlayerIdentity player) -> this.bindUuid(statement, "player", player.uuid());
            case BanTarget.IpTarget ip -> statement.bind("ip_start", ip.range().start()).bind("ip_end", ip.range().end());
            case BanTarget.IdTarget id -> statement.bind("id", id.id());
        };
    }

    // 绑定记录的玩家与 IP 列, 缺少的一项写入 NULL
    private <S extends SqlStatement<S>> S bindRecordTarget(S statement, BanRecord record) {
        this.bindUuid(statement, "player", record.player());
        if (record.playerName() == null) {
            statement.bindNull("player_name", Types.VARCHAR);
        } else {
            statement.bind("player_name", record.playerName());
        }
        IpRange ip = record.ip();
        if (ip == null) {
            return statement.bindNull("ip_start", Types.BIGINT).bindNull("ip_end", Types.BIGINT);
        }
        return statement.bind("ip_start", ip.start()).bind("ip_end", ip.end());
    }

    private <S extends SqlStatement<S>> S bindUuid(S statement, String name, @Nullable UUID uuid) {
        return uuid == null ? statement.bindNull(name, this.uuidNullType()) : statement.bind(name, this.uuidValue(uuid));
    }

    private BanRecord readBan(ResultSet result) throws SQLException {
        long start = result.getLong("ip_start");
        IpRange ip = result.wasNull() ? null : new IpRange(start, result.getLong("ip_end"));
        return new BanRecord(
                result.getString("id"),
                this.readUuid(result, "player"),
                result.getString("player_name"),
                ip,
                result.getString("reason"),
                result.getString("operator_name"),
                result.getString("server"),
                result.getLong("created_at"),
                result.getLong("expires_at"),
                result.getLong("revoked_at"),
                result.getString("revoked_by")
        );
    }
}
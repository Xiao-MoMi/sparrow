package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.util.IpRange;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.Update;
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
    private static final String WHERE = " WHERE ";
    private static final String SELECT_ALL = "SELECT * FROM ";
    private static final String AND = " AND ";
    private static final String PLAYER = "player";
    private static final String PLAYER_MATCH = PLAYER + " = :" + PLAYER;
    private static final String PLAYER_NAME = "player_name";
    private static final String IP_START = "ip_start";
    private static final String IP_END = "ip_end";

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
        String sql = SELECT_ALL + this.bans + WHERE + ACTIVE + AND + "(" + PLAYER_MATCH + " OR (" + IP_START + " <= :ip AND " + IP_END + " >= :ip))"
                + " ORDER BY CASE WHEN " + PLAYER_MATCH + " THEN 0 ELSE 1 END, CASE WHEN expires_at = 0 THEN 0 ELSE 1 END, expires_at DESC LIMIT 1";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindUuid(handle.createQuery(sql), PLAYER, player)
                .bind("ip", ip).bind("now", now).map((result, context) -> this.readBan(result)).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> saveBan(@NotNull BanRecord banRecord) {
        IpRange ip = banRecord.ip();
        // 带玩家的记录覆盖该玩家的旧封禁, 纯 IP 记录覆盖同一段的纯 IP 封禁
        String sameTarget = banRecord.player() != null ? PLAYER_MATCH : PLAYER + " IS NULL AND " + IP_START + " = :" + IP_START + " AND " + IP_END + " = :" + IP_END;
        String revoke = "UPDATE " + this.bans + " SET revoked_at = :now, revoked_by = :by" + WHERE + ACTIVE + AND + sameTarget;
        String insert = "INSERT INTO " + this.bans + " (id, " + PLAYER + ", " + PLAYER_NAME + ", " + IP_START + ", " + IP_END + ", reason, operator_name, server, created_at, expires_at)"
                + " VALUES (:id, :" + PLAYER + ", :" + PLAYER_NAME + ", :" + IP_START + ", :" + IP_END + ", :reason, :operator_name, :server, :created_at, :expires_at)";
        return CompletableFuture.supplyAsync(() -> this.sql().inTransaction(handle -> {
            Update revokeStatement = handle.createUpdate(revoke).bind("now", banRecord.createdAt()).bind("by", banRecord.operatorName());
            if (banRecord.player() != null) {
                this.bindUuid(revokeStatement, PLAYER, banRecord.player());
            } else {
                revokeStatement.bind(IP_START, ip.start()).bind(IP_END, ip.end());
            }
            int revoked = revokeStatement.execute();
            this.bindRecordTarget(handle.createUpdate(insert), banRecord)
                    .bind("id", banRecord.id())
                    .bind("reason", banRecord.reason())
                    .bind("operator_name", banRecord.operatorName())
                    .bind("server", banRecord.server())
                    .bind("created_at", banRecord.createdAt())
                    .bind("expires_at", banRecord.expiresAt())
                    .execute();
            return revoked > 0;
        }), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<BanRecord>> revokeBans(@NotNull BanTarget target, long now, @NotNull String revokedBy) {
        String match = switch (target) {
            case BanTarget.PlayerTarget ignored -> PLAYER_MATCH;
            case BanTarget.IpTarget ignored -> PLAYER + " IS NULL AND " + IP_START + " = :" + IP_START + " AND " + IP_END + " = :" + IP_END;
            case BanTarget.IdTarget ignored -> "id = :id";
        };
        String select = SELECT_ALL + this.bans + WHERE + ACTIVE + AND + match;
        String revoke = "UPDATE " + this.bans + " SET revoked_at = :now, revoked_by = :by WHERE id IN (<ids>)";
        return CompletableFuture.supplyAsync(() -> this.sql().inTransaction(handle -> {
            List<BanRecord> revoked = this.bindTarget(handle.createQuery(select), target).bind("now", now).map((result, context) -> this.readBan(result)).list();
            if (revoked.isEmpty()) return revoked;
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
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindQuery(handle.createQuery(sql), query).mapTo(Long.class).one()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<BanRecord>> listBans(@NotNull BanQuery query, int offset, int limit) {
        String sql = SELECT_ALL + this.bans + where(query) + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindQuery(handle.createQuery(sql), query)
                .bind("limit", limit).bind("offset", offset).map((result, context) -> this.readBan(result)).list()), this.executor);
    }

    // IP 目标匹配完整覆盖它的 IP 段, 包括账号加 IP 的封禁
    private static String where(BanQuery query) {
        List<String> conditions = new ArrayList<>(4);
        BanTarget target = query.target();
        if (target != null) {
            conditions.add(switch (target) {
                case BanTarget.PlayerTarget ignored -> PLAYER_MATCH;
                case BanTarget.IpTarget ignored -> IP_START + " <= :" + IP_START + AND + IP_END + " >= :" + IP_END;
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
        return conditions.isEmpty() ? "" : WHERE + String.join(AND, conditions);
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
            case BanTarget.PlayerTarget player -> this.bindUuid(statement, PLAYER, player.uuid());
            case BanTarget.IpTarget ip -> statement.bind(IP_START, ip.range().start()).bind(IP_END, ip.range().end());
            case BanTarget.IdTarget id -> statement.bind("id", id.id());
        };
    }

    // 绑定记录的玩家与 IP 列, 缺少的一项写入 NULL
    private <S extends SqlStatement<S>> S bindRecordTarget(S statement, BanRecord banRecord) {
        this.bindUuid(statement, PLAYER, banRecord.player());
        if (banRecord.playerName() == null) {
            statement.bindNull(PLAYER_NAME, Types.VARCHAR);
        } else {
            statement.bind(PLAYER_NAME, banRecord.playerName());
        }
        IpRange ip = banRecord.ip();
        if (ip == null) {
            return statement.bindNull(IP_START, Types.BIGINT).bindNull(IP_END, Types.BIGINT);
        }
        return statement.bind(IP_START, ip.start()).bind(IP_END, ip.end());
    }

    private <S extends SqlStatement<S>> S bindUuid(S statement, String name, @Nullable UUID uuid) {
        return uuid == null ? statement.bindNull(name, this.uuidNullType()) : statement.bind(name, this.uuidValue(uuid));
    }

    private BanRecord readBan(ResultSet result) throws SQLException {
        long start = result.getLong(IP_START);
        IpRange ip = result.wasNull() ? null : new IpRange(start, result.getLong(IP_END));
        return new BanRecord(result.getString("id"), this.readUuid(result, PLAYER), result.getString(PLAYER_NAME), ip,
                result.getString("reason"), result.getString("operator_name"), result.getString("server"),
                result.getLong("created_at"), result.getLong("expires_at"), result.getLong("revoked_at"), result.getString("revoked_by"));
    }
}

package net.momirealms.sparrow.database.mysql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.database.mysql.upgrade.MysqlSchemaMigration;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.IpRange;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public class MysqlDataStorage extends DataStorage {
    private static final MysqlServerVersion MINIMUM_SERVER_VERSION = new MysqlServerVersion(8, 0, 0);
    protected static final List<MysqlSchemaMigration> MIGRATIONS = List.of();

    private final String data;
    private final MysqlBanStore banStore;
    private final MysqlWarpStore warpStore;
    protected HikariDataSource pool;
    protected Jdbi jdbi;

    public MysqlDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
        this.data = "`" + this.namePrefix() + "data`";
        this.banStore = new MysqlBanStore(this::sql, executor, logger, this.namePrefix());
        this.warpStore = new MysqlWarpStore(this::sql, executor, logger, this.namePrefix());
    }

    @Override
    public void initialize() {
        PluginConfig.SqlOptions sqlOptions = this.options.mysql();
        HikariDataSource connected = new HikariDataSource();
        try {
            connected.setPoolName("sparrow-mysql");
            connected.setJdbcUrl(sqlOptions.url());
            connected.setDriverClassName(DependencyVersions.PROJECT_PACKAGE + ".libraries.mysql.cj.jdbc.Driver");
            connected.setUsername(sqlOptions.username());
            connected.setPassword(sqlOptions.password());
            connected.setMaximumPoolSize(10);
            connected.setConnectionTimeout(10_000);
            connected.setValidationTimeout(3_000);
            connected.setMaxLifetime(1_800_000);
            connected.setInitializationFailTimeout(10_000);
            connected.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            Jdbi jdbi = Jdbi.create(connected);
            this.verifyServerVersion(jdbi);
            new MysqlSchemaMigrator(this.logger, MysqlSchema.DATA_COMPONENT, MysqlSchema.DATA_TABLES, DependencyVersions.DATA_SCHEMA_VERSION, MysqlSchema::initializeData, MIGRATIONS).migrate(jdbi, this.namePrefix());
            this.pool = connected;
            this.jdbi = jdbi;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    private void verifyServerVersion(Jdbi jdbi) {
        String reported = jdbi.withHandle(handle -> handle.createQuery("SELECT VERSION()").mapTo(String.class).one());
        MysqlServerVersion version = MysqlServerVersion.parse(reported);
        if (version != null && version.atLeast(MINIMUM_SERVER_VERSION)) return;
        this.logger.error(TranslationManager.console(LogConstants.STORAGE_MYSQL_VERSION_UNSUPPORTED, reported, MINIMUM_SERVER_VERSION.toString()));
        throw new IllegalStateException("MySQL server version " + reported + " is not supported, MySQL " + MINIMUM_SERVER_VERSION + " or later is required");
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogin(@NotNull UUID player, @NotNull String name, long ip, long timestamp) {
        return this.save(player, name, timestamp, ip, null, null);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogout(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.save(player, name, timestamp, IpRange.NONE, server, location);
    }

    private CompletableFuture<Void> save(UUID player, String name, long timestamp, long ip, String server, WorldLocation location) {
        boolean logout = location != null;
        boolean withIp = ip != IpRange.NONE;
        String time = logout ? "last_logout" : "last_login";
        String columns = "player, name, " + time + ", updated_at" + (logout ? ", last_logout_server, last_logout_location" : "") + (withIp ? ", last_login_ip" : "");
        String values = ":player, :name, :time, :time" + (logout ? ", :server, :location" : "") + (withIp ? ", :ip" : "");
        String updates = "name = CASE WHEN :time >= updated_at THEN :name ELSE name END";
        // MySQL 按书写顺序赋值, 需要在 last_login 更新之前比较
        if (withIp) {
            updates += ", last_login_ip = CASE WHEN :time >= last_login THEN :ip ELSE last_login_ip END";
        }
        if (logout) {
            updates += ", last_logout_server = CASE WHEN :time >= last_logout THEN :server ELSE last_logout_server END"
                    + ", last_logout_location = CASE WHEN :time >= last_logout THEN :location ELSE last_logout_location END";
        }
        // 两服的异步写入可能交错, 登录与下线各自按事件时间更新.
        updates += ", " + time + " = GREATEST(" + time + ", :time), updated_at = GREATEST(updated_at, :time)";
        String upsert = "INSERT INTO " + this.data + " (" + columns + ") VALUES (" + values + ")"
                + " ON DUPLICATE KEY UPDATE " + updates;
        return CompletableFuture.runAsync(() -> {
            if (!this.storable(name)) {
                throw new IllegalArgumentException("Unsupported user name: '" + name + "'");
            }
            this.sql().useHandle(handle -> {
                var query = handle.createUpdate(upsert).bind("player", UUIDUtils.toBytes(player)).bind("name", name).bind("time", timestamp);
                if (logout) {
                    query.bind("server", server).bind("location", location.toJson());
                }
                if (withIp) {
                    query.bind("ip", ip);
                }
                query.execute();
            });
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> loadPlayer(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT * FROM " + this.data + " WHERE player = :player")
                .bind("player", UUIDUtils.toBytes(player)).map((result, context) -> readPlayer(result)).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Long> countPlayersOnIp(@NotNull IpRange range) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM " + this.data + " WHERE last_login_ip BETWEEN :start AND :end")
                .bind("start", range.start()).bind("end", range.end()).mapTo(Long.class).one()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<PlayerData>> listPlayersOnIp(@NotNull IpRange range, int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT * FROM " + this.data
                        + " WHERE last_login_ip BETWEEN :start AND :end ORDER BY last_login DESC, player DESC LIMIT :limit OFFSET :offset")
                .bind("start", range.start()).bind("end", range.end()).bind("limit", limit).bind("offset", offset)
                .map((result, context) -> readPlayer(result)).list()), this.executor);
    }

    private static PlayerData readPlayer(ResultSet result) throws SQLException {
        String json = result.getString("last_logout_location");
        WorldLocation location = json == null ? null : WorldLocation.fromJson(json);
        long ip = result.getLong("last_login_ip");
        String lastIp = result.wasNull() ? null : IpRange.format(ip);
        return new PlayerData(UUIDUtils.fromBytes(result.getBytes("player")), result.getString("name"), result.getLong("last_login"), result.getLong("last_logout"),
                result.getString("last_logout_server"), location, lastIp, result.getLong("updated_at"));
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        if (!this.storable(name)) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT player FROM " + this.data + " WHERE name = :name ORDER BY updated_at DESC, player DESC LIMIT 1")
                .bind("name", name).map((result, context) -> UUIDUtils.fromBytes(result.getBytes("player"))).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT name FROM " + this.data + " WHERE player = :player")
                .bind("player", UUIDUtils.toBytes(player)).mapTo(String.class).findOne()), this.executor);
    }

    @Override
    @NotNull
    public BanStore banStore() {
        return this.banStore;
    }

    @Override
    @NotNull
    public WarpStore warpStore() {
        return this.warpStore;
    }

    private Jdbi sql() {
        if (this.jdbi == null) {
            throw new IllegalStateException("SQL database is not initialized");
        }
        return this.jdbi;
    }

    // VARCHAR(64) 按码点计数; MySQL 的 utf8mb4_bin 比较时忽略尾随空格, 带尾随空格的名字无法精确匹配.
    private boolean storable(String name) {
        return name.codePointCount(0, name.length()) <= 64 && !name.endsWith(" ");
    }

    @Override
    public void close() {
        if (this.pool != null) {
            this.pool.close();
        }
    }
}

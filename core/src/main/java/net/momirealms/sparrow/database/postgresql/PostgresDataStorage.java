package net.momirealms.sparrow.database.postgresql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.database.postgresql.upgrade.PostgresSchemaMigration;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.IpRange;
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
public final class PostgresDataStorage extends DataStorage {
    private static final List<PostgresSchemaMigration> MIGRATIONS = List.of();

    private final String data;
    private final PostgresBanStore banStore;
    private HikariDataSource pool;
    private Jdbi jdbi;

    public PostgresDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
        this.data = "\"" + this.namePrefix() + "data\"";
        this.banStore = new PostgresBanStore(this::sql, executor, logger, this.namePrefix());
    }

    @Override
    public void initialize() {
        PluginConfig.SqlOptions sqlOptions = this.options.postgresql();
        HikariDataSource connected = new HikariDataSource();
        try {
            connected.setPoolName("sparrow-postgresql");
            connected.setJdbcUrl(sqlOptions.url());
            connected.setDriverClassName(DependencyVersions.PROJECT_PACKAGE + ".libraries.postgresql.Driver");
            connected.setUsername(sqlOptions.username());
            connected.setPassword(sqlOptions.password());
            connected.setMaximumPoolSize(10);
            connected.setConnectionTimeout(10_000);
            connected.setValidationTimeout(3_000);
            connected.setMaxLifetime(1_800_000);
            connected.setInitializationFailTimeout(10_000);
            connected.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            Jdbi jdbi = Jdbi.create(connected);
            new PostgresSchemaMigrator(this.logger, PostgresSchema.DATA_COMPONENT, PostgresSchema.DATA_TABLES, DependencyVersions.DATA_SCHEMA_VERSION, PostgresSchema::initializeData, MIGRATIONS).migrate(jdbi, this.namePrefix());
            this.pool = connected;
            this.jdbi = jdbi;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
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
        String values = ":player, :name, :time, :time" + (logout ? ", :server, CAST(:location AS JSONB)" : "") + (withIp ? ", :ip" : "");
        String table = this.data + ".";
        String updates = "name = CASE WHEN :time >= " + table + "updated_at THEN :name ELSE " + table + "name END";
        if (withIp) {
            updates += ", last_login_ip = CASE WHEN :time >= " + table + "last_login THEN EXCLUDED.last_login_ip ELSE " + table + "last_login_ip END";
        }
        if (logout) {
            String[] fields = {"last_logout_server", "last_logout_location"};
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                updates += ", " + field + " = CASE WHEN :time >= " + table + time + " THEN EXCLUDED." + field + " ELSE " + table + field + " END";
            }
        }
        // 两服的异步写入可能交错, 登录与下线各自按事件时间更新.
        updates += ", " + time + " = GREATEST(" + table + time + ", :time), updated_at = GREATEST(" + table + "updated_at, :time)";
        String upsert = "INSERT INTO " + this.data + " (" + columns + ") VALUES (" + values + ")"
                + " ON CONFLICT (player) DO UPDATE SET " + updates;
        return CompletableFuture.runAsync(() -> {
            if (!this.storable(name)) {
                throw new IllegalArgumentException("Unsupported user name: '" + name + "'");
            }
            this.sql().useHandle(handle -> {
                var query = handle.createUpdate(upsert).bind("player", player).bind("name", name).bind("time", timestamp);
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
                .bind("player", player).map((result, context) -> readPlayer(result)).findOne()), this.executor);
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
        return new PlayerData(result.getObject("player", UUID.class), result.getString("name"), result.getLong("last_login"), result.getLong("last_logout"),
                result.getString("last_logout_server"), location, lastIp, result.getLong("updated_at"));
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        if (!this.storable(name)) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT player FROM " + this.data + " WHERE name = :name ORDER BY updated_at DESC, player DESC LIMIT 1")
                .bind("name", name).map((result, context) -> result.getObject("player", UUID.class)).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT name FROM " + this.data + " WHERE player = :player")
                .bind("player", player).mapTo(String.class).findOne()), this.executor);
    }

    @Override
    @NotNull
    public BanStore banStore() {
        return this.banStore;
    }

    private Jdbi sql() {
        if (this.jdbi == null) {
            throw new IllegalStateException("SQL database is not initialized");
        }
        return this.jdbi;
    }

    // VARCHAR(64) 按码点计数.
    private boolean storable(String name) {
        return name.codePointCount(0, name.length()) <= 64;
    }

    @Override
    public void close() {
        if (this.pool != null) {
            this.pool.close();
        }
    }
}

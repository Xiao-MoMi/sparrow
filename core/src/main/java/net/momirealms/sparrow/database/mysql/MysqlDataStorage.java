package net.momirealms.sparrow.database.mysql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.database.mysql.upgrade.MysqlSchemaMigration;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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
    protected HikariDataSource pool;
    protected Jdbi jdbi;

    public MysqlDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
        this.data = "`" + this.namePrefix() + "data`";
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
            new MysqlSchemaMigrator(this.logger, MysqlSchema.CURRENT_VERSION, MysqlSchema::initialize, MIGRATIONS).migrate(jdbi, this.namePrefix());
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
    public CompletableFuture<Void> saveLogin(@NotNull UUID player, @NotNull String name, long timestamp) {
        return this.save(player, name, timestamp, null, null);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogout(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.save(player, name, timestamp, server, location);
    }

    private CompletableFuture<Void> save(UUID player, String name, long timestamp, String server, WorldLocation location) {
        boolean logout = location != null;
        String time = logout ? "last_logout" : "last_login";
        String columns = "player, name, " + time + ", updated_at" + (logout ? ", last_server, last_world, x, y, z, yaw, pitch" : "");
        String values = ":player, :name, :time, :time" + (logout ? ", :server, :world, :x, :y, :z, :yaw, :pitch" : "");
        String updates = "name = CASE WHEN :time >= updated_at THEN :name ELSE name END";
        if (logout) {
            String[] fields = {"last_server", "last_world", "x", "y", "z", "yaw", "pitch"};
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                String parameter = field.replace("last_", "");
                updates += ", " + field + " = CASE WHEN :time >= " + time + " THEN :" + parameter + " ELSE " + field + " END";
            }
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
                    query.bind("server", server).bind("world", location.world()).bind("x", location.x()).bind("y", location.y())
                            .bind("z", location.z()).bind("yaw", location.yaw()).bind("pitch", location.pitch());
                }
                query.execute();
            });
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> loadPlayer(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT * FROM " + this.data + " WHERE player = :player")
                .bind("player", UUIDUtils.toBytes(player)).map((result, context) -> {
                    String world = result.getString("last_world");
                    WorldLocation location = world == null ? null : new WorldLocation(world, result.getDouble("x"), result.getDouble("y"), result.getDouble("z"), result.getFloat("yaw"), result.getFloat("pitch"));
                    return new PlayerData(player, result.getString("name"), result.getLong("last_login"), result.getLong("last_logout"), result.getString("last_server"), location, result.getLong("updated_at"));
                }).findOne()), this.executor);
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

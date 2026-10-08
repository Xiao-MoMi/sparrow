package net.momirealms.sparrow.database.mysql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.database.SpawnStore;
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
import org.jdbi.v3.core.statement.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private final MysqlHomeStore homeStore;
    private final MysqlSpawnStore spawnStore;
    protected HikariDataSource pool;
    protected Jdbi jdbi;

    public MysqlDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
        this.data = "`" + this.namePrefix() + "data`";
        this.banStore = new MysqlBanStore(this::sql, executor, logger, this.namePrefix());
        this.warpStore = new MysqlWarpStore(this::sql, executor, logger, this.namePrefix());
        this.homeStore = new MysqlHomeStore(this::sql, executor, logger, this.namePrefix());
        this.spawnStore = new MysqlSpawnStore(this::sql, executor, logger, this.namePrefix());
    }

    @Override
    public void initialize() {
        PluginConfig.SqlOptions sqlOptions = super.options.mysql();
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
            new MysqlSchemaMigrator(
                    super.logger,
                    MysqlSchema.DATA_COMPONENT,
                    MysqlSchema.DATA_TABLES,
                    DependencyVersions.DATA_SCHEMA_VERSION,
                    MysqlSchema::initializeData,
                    MIGRATIONS
            ).migrate(jdbi, this.namePrefix());
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
        if (version != null && version.atLeast(MINIMUM_SERVER_VERSION)) {
            return;
        }
        super.logger.error(TranslationManager.console(LogConstants.STORAGE_MYSQL_VERSION_UNSUPPORTED, reported, MINIMUM_SERVER_VERSION.toString()));
        throw new IllegalStateException(
                "MySQL server version " + reported + " is not supported, MySQL " + MINIMUM_SERVER_VERSION + " or later is required"
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogin(@NotNull UUID player, @NotNull String name, long ip, long timestamp) {
        return this.save(player, name, timestamp, ip, null, null, "last_login");
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogout(
            @NotNull UUID player,
            @NotNull String name,
            long timestamp,
            @NotNull String server,
            @NotNull WorldLocation location
    ) {
        return this.save(player, name, timestamp, IpRange.NONE, server, location, "last_logout");
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveDeath(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.save(player, name, timestamp, IpRange.NONE, server, location, "last_death");
    }

    @NotNull
    private CompletableFuture<Void> save(UUID player, String name, long timestamp, long ip, @Nullable String server, @Nullable WorldLocation location, String time) {
        boolean withLocation = location != null;
        boolean withIp = ip != IpRange.NONE;
        String columns = "player, name, " + time + ", updated_at" + (withLocation ? ", " + time + "_server, " + time + "_location" : "") + (withIp ? ", last_login_ip" : "");
        String values = ":player, :name, :time, :time" + (withLocation ? ", :server, :location" : "") + (withIp ? ", :ip" : "");
        String updates = "name = CASE WHEN :time >= updated_at THEN :name ELSE name END";
        // MySQL 按书写顺序赋值, 需要在 last_login 更新之前比较
        if (withIp) {
            updates += ", last_login_ip = CASE WHEN :time >= last_login THEN :ip ELSE last_login_ip END";
        }
        if (withLocation) {
            updates += ", " + time + "_server = CASE WHEN :time >= " + time + " THEN :server ELSE " + time + "_server END"
                    + ", " + time + "_location = CASE WHEN :time >= " + time + " THEN :location ELSE " + time + "_location END";
        }
        // 各类事件按自己的时间更新位置与状态
        updates += ", " + time + " = GREATEST(" + time + ", :time), updated_at = GREATEST(updated_at, :time)";
        String upsert = "INSERT INTO " + this.data + " (" + columns + ") VALUES (" + values + ")"
                + " ON DUPLICATE KEY UPDATE " + updates;
        return CompletableFuture.runAsync(() -> {
                    if (!this.storable(name)) {
                        throw new IllegalArgumentException("Unsupported user name: '" + name + "'");
                    }
                    this.sql().useHandle(handle -> {
                        Update query = handle.createUpdate(upsert)
                                .bind("player", UUIDUtils.toBytes(player))
                                .bind("name", name)
                                .bind("time", timestamp);
                        if (withLocation) {
                            query.bind("server", server).bind("location", location.toJson());
                        }
                        if (withIp) {
                            query.bind("ip", ip);
                        }
                        query.execute();
                    });
                }, super.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> loadPlayer(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(
                () -> this.sql().withHandle(handle -> handle.createQuery("SELECT * FROM " + this.data + " WHERE player = :player")
                        .bind("player", UUIDUtils.toBytes(player))
                        .map((result, context) -> readPlayer(result))
                        .findOne()),
                super.executor
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Long> countPlayersOnIp(@NotNull IpRange range) {
        return CompletableFuture.supplyAsync(
                () -> this.sql().withHandle(handle -> handle.createQuery(
                        "SELECT COUNT(*) FROM " + this.data + " WHERE last_login_ip BETWEEN :start AND :end"
                )
                        .bind("start", range.start())
                        .bind("end", range.end())
                        .mapTo(Long.class)
                        .one()),
                super.executor
        );
    }

    @Override
    @NotNull
    public CompletableFuture<List<PlayerData>> listPlayersOnIp(@NotNull IpRange range, int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT * FROM " + this.data
                        + " WHERE last_login_ip BETWEEN :start AND :end ORDER BY last_login DESC, player DESC LIMIT :limit OFFSET :offset")
                .bind("start", range.start())
                .bind("end", range.end())
                .bind("limit", limit)
                .bind("offset", offset)
                .map((result, context) -> readPlayer(result))
                .list()), super.executor);
    }

    private static PlayerData readPlayer(ResultSet result) throws SQLException {
        String json = result.getString("last_logout_location");
        WorldLocation location = json == null ? null : WorldLocation.fromJson(json);
        String deathJson = result.getString("last_death_location");
        WorldLocation deathLocation = deathJson == null ? null : WorldLocation.fromJson(deathJson);
        long ip = result.getLong("last_login_ip");
        String lastIp = result.wasNull() ? null : IpRange.format(ip);
        return new PlayerData(
                UUIDUtils.fromBytes(result.getBytes("player")),
                result.getString("name"),
                result.getLong("last_login"),
                result.getLong("last_logout"),
                result.getString("last_logout_server"),
                location,
                result.getLong("last_death"),
                result.getString("last_death_server"),
                deathLocation,
                lastIp,
                result.getLong("updated_at")
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        if (!this.storable(name)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(
                () -> this.sql().withHandle(handle -> handle.createQuery(
                        "SELECT player FROM " + this.data + " WHERE name = :name ORDER BY updated_at DESC, player DESC LIMIT 1"
                )
                        .bind("name", name)
                        .map((result, context) -> UUIDUtils.fromBytes(result.getBytes("player")))
                        .findOne()),
                super.executor
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(
                () -> this.sql().withHandle(handle -> handle.createQuery("SELECT name FROM " + this.data + " WHERE player = :player")
                        .bind("player", UUIDUtils.toBytes(player))
                        .mapTo(String.class)
                        .findOne()),
                super.executor
        );
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

    @Override
    @NotNull
    public HomeStore homeStore() {
        return this.homeStore;
    }

    @NotNull
    @Override
    public SpawnStore spawnStore() {
        return this.spawnStore;
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
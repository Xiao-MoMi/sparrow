package net.momirealms.sparrow.database.postgresql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.database.SpawnStore;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.database.postgresql.upgrade.PostgresSchemaMigration;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.IpRange;
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
public final class PostgresDataStorage extends DataStorage {
    private static final List<PostgresSchemaMigration> MIGRATIONS = List.of();

    private final String data;
    private final PostgresBanStore banStore;
    private final PostgresWarpStore warpStore;
    private final PostgresHomeStore homeStore;
    private final PostgresSpawnStore spawnStore;
    private HikariDataSource pool;
    private Jdbi jdbi;

    public PostgresDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
        this.data = "\"" + this.namePrefix() + "data\"";
        this.banStore = new PostgresBanStore(this::sql, executor, logger, this.namePrefix());
        this.warpStore = new PostgresWarpStore(this::sql, executor, logger, this.namePrefix());
        this.homeStore = new PostgresHomeStore(this::sql, executor, logger, this.namePrefix());
        this.spawnStore = new PostgresSpawnStore(this::sql, executor, logger, this.namePrefix());
    }

    @Override
    public void initialize() {
        PluginConfig.SqlOptions sqlOptions = super.options.postgresql();
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
        return this.save(player, name, timestamp, ip, null, null, "last_login");
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogout(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.save(player, name, timestamp, IpRange.NONE, server, location, "last_logout");
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveDeath(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.save(player, name, timestamp, IpRange.NONE, server, location, "last_death");
    }

    @NotNull
    private CompletableFuture<Void> save(
            @NotNull UUID player,
            @NotNull String name,
            long timestamp,
            long ip,
            @Nullable String server,
            @Nullable WorldLocation location,
            @NotNull String time
    ) {
        boolean withLocation = location != null;
        boolean withIp = ip != IpRange.NONE;
        String columns = "player, name, " + time + ", updated_at" + (withLocation ? ", " + time + "_server, " + time + "_location" : "")
                + (withIp ? ", last_login_ip" : "");
        String values = ":player, :name, :time, :time" + (withLocation ? ", :server, CAST(:location AS JSONB)" : "") + (withIp ? ", :ip" : "");
        String table = this.data + ".";
        String updates = "name = CASE WHEN :time >= " + table + "updated_at THEN :name ELSE " + table + "name END";
        if (withIp) {
            updates += ", last_login_ip = CASE WHEN :time >= " + table + "last_login THEN EXCLUDED.last_login_ip ELSE " + table + "last_login_ip END";
        }
        if (withLocation) {
            String[] fields = {time + "_server", time + "_location"};
            for (String field : fields) {
                updates += ", " + field + " = CASE WHEN :time >= " + table + time + " THEN EXCLUDED." + field + " ELSE " + table + field + " END";
            }
        }
        // 各类事件按自己的时间更新位置与状态
        updates += ", " + time + " = GREATEST(" + table + time + ", :time), updated_at = GREATEST(" + table + "updated_at, :time)";
        String upsert = "INSERT INTO " + this.data + " (" + columns + ") VALUES (" + values + ")"
                + " ON CONFLICT (player) DO UPDATE SET " + updates;
        return CompletableFuture.runAsync(
                () -> {
                    if (!this.storable(name)) {
                        throw new IllegalArgumentException("Unsupported user name: '" + name + "'");
                    }
                    this.sql().useHandle(handle -> {
                        Update query = handle.createUpdate(upsert)
                                .bind("player", player)
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
                },
                super.executor
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> loadPlayer(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(
                () -> this.sql().withHandle(handle -> handle.createQuery("SELECT * FROM " + this.data + " WHERE player = :player")
                        .bind("player", player)
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
                result.getObject("player", UUID.class),
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
                        .map((result, context) -> result.getObject("player", UUID.class))
                        .findOne()),
                super.executor
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(
                () -> this.sql().withHandle(handle -> handle.createQuery("SELECT name FROM " + this.data + " WHERE player = :player")
                        .bind("player", player)
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

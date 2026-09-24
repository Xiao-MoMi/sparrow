package net.momirealms.sparrow.database;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

// MySQL 与 MariaDB 用 BINARY(16) 大端序保存 UUID, PostgreSQL 使用原生 UUID 类型.
final class SqlDataStorage extends DataStorage {
    private static final String MYSQL_TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin"; // 名字比较区分大小写

    private final boolean postgres;
    private final String users;
    private HikariDataSource pool;
    private Jdbi jdbi;

    SqlDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor) {
        super(options, executor);
        this.postgres = options.type() == DatabaseType.POSTGRESQL;
        this.users = this.quote(this.namePrefix() + "users");
    }

    @Override
    public void initialize() {
        PluginConfig.SqlOptions sqlOptions;
        String driverClassName;
        String libraries = DependencyVersions.PROJECT_PACKAGE + ".libraries.";
        switch (this.options.type()) {
            case MYSQL -> {
                sqlOptions = this.options.mysql();
                driverClassName = libraries + "mysql.cj.jdbc.Driver";
            }
            case MARIADB -> {
                sqlOptions = this.options.mariadb();
                driverClassName = libraries + "mariadb.Driver";
            }
            case POSTGRESQL -> {
                sqlOptions = this.options.postgresql();
                driverClassName = libraries + "postgresql.Driver";
            }
            default -> throw new IllegalStateException("SQL backend is not selected");
        }
        HikariDataSource connected = new HikariDataSource();
        try {
            connected.setPoolName("sparrow-" + this.options.type().name().toLowerCase(Locale.ROOT));
            connected.setJdbcUrl(sqlOptions.url());
            connected.setDriverClassName(driverClassName);
            connected.setUsername(sqlOptions.username());
            connected.setPassword(sqlOptions.password());
            connected.setMaximumPoolSize(10);
            connected.setConnectionTimeout(10_000);
            connected.setValidationTimeout(3_000);
            connected.setMaxLifetime(1_800_000);
            connected.setInitializationFailTimeout(10_000);
            connected.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            Jdbi jdbi = Jdbi.create(connected);
            this.createSchema(jdbi);
            this.pool = connected;
            this.jdbi = jdbi;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    // 名字索引带上最后出现时间, 同名记录可直接取最近使用者.
    private void createSchema(Jdbi jdbi) {
        jdbi.useHandle(handle -> {
            if (this.postgres) {
                handle.execute("CREATE TABLE IF NOT EXISTS " + this.users + " (player UUID PRIMARY KEY, name VARCHAR(64) COLLATE \"C\" NOT NULL, last_seen BIGINT NOT NULL)");
                handle.execute("CREATE INDEX IF NOT EXISTS " + this.quote(this.namePrefix() + "user_name_seen") + " ON " + this.users + " (name, last_seen, player)");
            } else {
                handle.execute("CREATE TABLE IF NOT EXISTS " + this.users + " (`player` BINARY(16) NOT NULL PRIMARY KEY, `name` VARCHAR(64) NOT NULL, `last_seen` BIGINT NOT NULL, "
                        + "KEY `user_name_seen` (`name`, `last_seen`))" + MYSQL_TABLE_OPTIONS);
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveUser(@NotNull UUID player, @NotNull String name) {
        String upsert = this.postgres
                ? "INSERT INTO " + this.users + " (player, name, last_seen) VALUES (:player, :name, :lastSeen) ON CONFLICT (player) DO UPDATE SET name = EXCLUDED.name, last_seen = EXCLUDED.last_seen"
                : "INSERT INTO " + this.users + " (`player`, `name`, `last_seen`) VALUES (:player, :name, :lastSeen) ON DUPLICATE KEY UPDATE `name` = :name, `last_seen` = :lastSeen";
        return CompletableFuture.runAsync(() -> {
            if (!this.storable(name)) throw new IllegalArgumentException("Unsupported user name: '" + name + "'");
            this.sql().useHandle(handle -> handle.createUpdate(upsert)
                    .bind("player", this.uuidArgument(player)).bind("name", name).bind("lastSeen", System.currentTimeMillis()).execute());
        }, this.executor);
    }

    // 同名记录取最近会话, 同毫秒时按 UUID 保持稳定顺序.
    @Override
    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        if (!this.storable(name)) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT player FROM " + this.users + " WHERE name = :name ORDER BY last_seen DESC, player DESC LIMIT 1")
                .bind("name", name).map((result, context) -> this.readUuid(result)).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery("SELECT name FROM " + this.users + " WHERE player = :player")
                .bind("player", this.uuidArgument(player)).mapTo(String.class).findOne()), this.executor);
    }

    private Jdbi sql() {
        if (this.jdbi == null) throw new IllegalStateException("SQL database is not initialized");
        return this.jdbi;
    }

    private Object uuidArgument(UUID uuid) {
        return this.postgres ? uuid : UUIDUtils.toBytes(uuid);
    }

    private UUID readUuid(ResultSet result) throws SQLException {
        return this.postgres ? result.getObject("player", UUID.class) : UUIDUtils.fromBytes(result.getBytes("player"));
    }

    // VARCHAR(64) 按码点计数; MySQL 的 utf8mb4_bin 比较时忽略尾随空格, 带尾随空格的名字无法精确匹配.
    private boolean storable(String name) {
        return name.codePointCount(0, name.length()) <= 64 && (this.postgres || !name.endsWith(" "));
    }

    private String quote(String identifier) {
        return this.postgres ? "\"" + identifier + "\"" : "`" + identifier + "`";
    }

    @Override
    public void close() {
        if (this.pool != null) this.pool.close();
    }
}

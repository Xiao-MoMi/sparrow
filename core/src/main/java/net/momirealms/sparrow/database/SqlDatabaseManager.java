package net.momirealms.sparrow.database;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class SqlDatabaseManager extends DatabaseManager {
    private HikariDataSource pool;
    private Jdbi jdbi;

    SqlDatabaseManager(@NotNull PluginConfig.DatabaseOptions options) {
        super(options);
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
            jdbi.withHandle(handle -> handle.createQuery("SELECT 1").mapTo(int.class).one());
            this.pool = connected;
            this.jdbi = jdbi;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    /**
     * 返回共用连接池的 Jdbi。执行 JDBC 操作会阻塞当前线程。
     *
     * @return 已连接数据库的 Jdbi
     * @throws IllegalStateException 连接尚未建立时
     */
    @NotNull
    public Jdbi sql() {
        if (this.jdbi == null) throw new IllegalStateException("SQL database is not initialized");
        return this.jdbi;
    }

    @Override
    public void close() {
        if (this.pool != null) this.pool.close();
    }
}

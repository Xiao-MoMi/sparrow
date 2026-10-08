package net.momirealms.sparrow.database.mariadb;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.mysql.MysqlDataStorage;
import net.momirealms.sparrow.database.mysql.MysqlSchema;
import net.momirealms.sparrow.database.mysql.MysqlSchemaMigrator;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class MariaDbDataStorage extends MysqlDataStorage {

    public MariaDbDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
    }

    @Override
    public void initialize() {
        PluginConfig.SqlOptions sqlOptions = super.options.mariadb();
        HikariDataSource connected = new HikariDataSource();
        try {
            connected.setPoolName("sparrow-mariadb");
            connected.setJdbcUrl(sqlOptions.url());
            connected.setDriverClassName(DependencyVersions.PROJECT_PACKAGE + ".libraries.mariadb.Driver");
            connected.setUsername(sqlOptions.username());
            connected.setPassword(sqlOptions.password());
            connected.setMaximumPoolSize(10);
            connected.setConnectionTimeout(10_000);
            connected.setValidationTimeout(3_000);
            connected.setMaxLifetime(1_800_000);
            connected.setInitializationFailTimeout(10_000);
            connected.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
            Jdbi jdbi = Jdbi.create(connected);
            new MysqlSchemaMigrator(
                    super.logger,
                    MysqlSchema.DATA_COMPONENT,
                    MysqlSchema.DATA_TABLES,
                    DependencyVersions.DATA_SCHEMA_VERSION,
                    MysqlSchema::initializeData,
                    MIGRATIONS
            ).migrate(jdbi, this.namePrefix());
            super.pool = connected;
            super.jdbi = jdbi;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }
}

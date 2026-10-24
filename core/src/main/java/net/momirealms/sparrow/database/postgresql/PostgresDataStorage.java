package net.momirealms.sparrow.database.postgresql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.PlayerProfileStore;
import net.momirealms.sparrow.database.PlayerNameHistoryStore;
import net.momirealms.sparrow.database.PlayerIpHistoryStore;
import net.momirealms.sparrow.database.postgresql.upgrade.PostgresSchemaMigration;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class PostgresDataStorage extends DataStorage {
    private static final List<PostgresSchemaMigration> MIGRATIONS = List.of();

    private HikariDataSource pool;
    private PostgresPlayerProfileStore profileStore;
    private PostgresPlayerNameHistoryStore nameHistoryStore;
    private PostgresPlayerIpHistoryStore ipHistoryStore;

    public PostgresDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
    }

    @Override
    public void initialize() {
        PluginConfig.DatabaseOptions sqlOptions = super.options;
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
            this.profileStore = new PostgresPlayerProfileStore(jdbi, super.executor, this.namePrefix());
            this.nameHistoryStore = new PostgresPlayerNameHistoryStore(jdbi, super.executor, this.namePrefix());
            this.ipHistoryStore = new PostgresPlayerIpHistoryStore(jdbi, super.executor, this.namePrefix());
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    @NotNull
    @Override
    public PlayerProfileStore playerProfileStore() {
        return this.profileStore;
    }

    @NotNull
    @Override
    public PlayerNameHistoryStore playerNameHistoryStore() {
        return this.nameHistoryStore;
    }

    @NotNull
    @Override
    public PlayerIpHistoryStore playerIpHistoryStore() {
        return this.ipHistoryStore;
    }

    @Override
    public void close() {
        if (this.pool != null) {
            this.pool.close();
        }
    }
}
package net.momirealms.sparrow.database.postgresql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.BackStore;
import net.momirealms.sparrow.database.WarpStore;
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
    private static final List<PostgresSchemaMigration> DATA_MIGRATIONS = List.of();
    private static final List<PostgresSchemaMigration> NAME_HISTORY_MIGRATIONS = List.of();
    private static final List<PostgresSchemaMigration> IP_HISTORY_MIGRATIONS = List.of();

    private HikariDataSource pool;
    private PostgresPlayerProfileStore profileStore;
    private PostgresBackStore backStore;
    private PostgresWarpStore warpStore;
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
            this.initializeSchemas(jdbi);
            this.pool = connected;
            this.backStore = new PostgresBackStore(jdbi, super.executor, this.namePrefix());
            this.warpStore = new PostgresWarpStore(jdbi, super.executor, this.namePrefix());
            this.profileStore = new PostgresPlayerProfileStore(jdbi, super.executor, this.namePrefix());
            this.nameHistoryStore = new PostgresPlayerNameHistoryStore(jdbi, super.executor, this.namePrefix());
            this.ipHistoryStore = new PostgresPlayerIpHistoryStore(jdbi, super.executor, this.namePrefix());
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    private void initializeSchemas(@NotNull Jdbi jdbi) {
        new PostgresSchemaMigrator(
                this.logger,
                PostgresSchema.WARP_COMPONENT,
                PostgresSchema.WARP_TABLES,
                DependencyVersions.WARP_SCHEMA_VERSION,
                PostgresSchema::initializeWarps,
                List.of()
        ).migrate(jdbi, this.namePrefix());
        new PostgresSchemaMigrator(
                this.logger,
                PostgresSchema.BACK_COMPONENT,
                PostgresSchema.BACK_TABLES,
                DependencyVersions.BACK_SCHEMA_VERSION,
                PostgresSchema::initializeBack,
                List.of()
        ).migrate(jdbi, this.namePrefix());
        new PostgresSchemaMigrator(
                this.logger,
                PostgresSchema.DATA_COMPONENT,
                PostgresSchema.DATA_TABLES,
                DependencyVersions.DATA_SCHEMA_VERSION,
                PostgresSchema::initializeData,
                DATA_MIGRATIONS
        ).migrate(jdbi, this.namePrefix());
        new PostgresSchemaMigrator(
                this.logger,
                PostgresSchema.NAME_HISTORY_COMPONENT,
                PostgresSchema.NAME_HISTORY_TABLES,
                DependencyVersions.PLAYER_NAME_HISTORY_SCHEMA_VERSION,
                PostgresSchema::initializeNameHistory,
                NAME_HISTORY_MIGRATIONS
        ).migrate(jdbi, this.namePrefix());
        new PostgresSchemaMigrator(
                this.logger,
                PostgresSchema.IP_HISTORY_COMPONENT,
                PostgresSchema.IP_HISTORY_TABLES,
                DependencyVersions.PLAYER_IP_HISTORY_SCHEMA_VERSION,
                PostgresSchema::initializeIpHistory,
                IP_HISTORY_MIGRATIONS
        ).migrate(jdbi, this.namePrefix());
    }

    @NotNull
    @Override
    public WarpStore warpStore() {
        return this.warpStore;
    }

    @NotNull
    @Override
    public BackStore backStore() {
        return this.backStore;
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
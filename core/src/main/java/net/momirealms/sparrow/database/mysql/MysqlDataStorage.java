package net.momirealms.sparrow.database.mysql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.database.BackStore;
import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.database.PlayerProfileStore;
import net.momirealms.sparrow.database.PlayerNameHistoryStore;
import net.momirealms.sparrow.database.PlayerIpHistoryStore;
import net.momirealms.sparrow.database.mysql.upgrade.MysqlSchemaMigration;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public class MysqlDataStorage extends DataStorage {
    private static final MysqlServerVersion MINIMUM_SERVER_VERSION = new MysqlServerVersion(8, 0, 0);
    private static final List<MysqlSchemaMigration> DATA_MIGRATIONS = List.of();
    private static final List<MysqlSchemaMigration> NAME_HISTORY_MIGRATIONS = List.of();
    private static final List<MysqlSchemaMigration> IP_HISTORY_MIGRATIONS = List.of();

    protected HikariDataSource pool;
    protected MysqlPlayerProfileStore profileStore;
    protected MysqlBackStore backStore;
    protected MysqlWarpStore warpStore;
    protected MysqlHomeStore homeStore;
    protected MysqlPlayerNameHistoryStore nameHistoryStore;
    protected MysqlPlayerIpHistoryStore ipHistoryStore;

    public MysqlDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
    }

    @Override
    public void initialize() {
        PluginConfig.DatabaseOptions sqlOptions = super.options;
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
            this.initializeSchemas(jdbi);
            this.pool = connected;
            this.backStore = new MysqlBackStore(jdbi, super.executor, this.namePrefix());
            this.warpStore = new MysqlWarpStore(jdbi, super.executor, this.namePrefix());
            this.homeStore = new MysqlHomeStore(jdbi, super.executor, this.namePrefix());
            this.profileStore = new MysqlPlayerProfileStore(jdbi, super.executor, this.namePrefix());
            this.nameHistoryStore = new MysqlPlayerNameHistoryStore(jdbi, super.executor, this.namePrefix());
            this.ipHistoryStore = new MysqlPlayerIpHistoryStore(jdbi, super.executor, this.namePrefix());
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    protected void initializeSchemas(@NotNull Jdbi jdbi) {
        new MysqlSchemaMigrator(
                this.logger,
                MysqlSchema.HOME_COMPONENT,
                MysqlSchema.HOME_TABLES,
                DependencyVersions.HOME_SCHEMA_VERSION,
                MysqlSchema::initializeHomes,
                List.of()
        ).migrate(jdbi, this.namePrefix());
        new MysqlSchemaMigrator(
                this.logger,
                MysqlSchema.WARP_COMPONENT,
                MysqlSchema.WARP_TABLES,
                DependencyVersions.WARP_SCHEMA_VERSION,
                MysqlSchema::initializeWarps,
                List.of()
        ).migrate(jdbi, this.namePrefix());
        new MysqlSchemaMigrator(
                this.logger,
                MysqlSchema.BACK_COMPONENT,
                MysqlSchema.BACK_TABLES,
                DependencyVersions.BACK_SCHEMA_VERSION,
                MysqlSchema::initializeBack,
                List.of()
        ).migrate(jdbi, this.namePrefix());
        new MysqlSchemaMigrator(
                this.logger,
                MysqlSchema.DATA_COMPONENT,
                MysqlSchema.DATA_TABLES,
                DependencyVersions.DATA_SCHEMA_VERSION,
                MysqlSchema::initializeData,
                DATA_MIGRATIONS
        ).migrate(jdbi, this.namePrefix());
        new MysqlSchemaMigrator(
                this.logger,
                MysqlSchema.NAME_HISTORY_COMPONENT,
                MysqlSchema.NAME_HISTORY_TABLES,
                DependencyVersions.PLAYER_NAME_HISTORY_SCHEMA_VERSION,
                MysqlSchema::initializeNameHistory,
                NAME_HISTORY_MIGRATIONS
        ).migrate(jdbi, this.namePrefix());
        new MysqlSchemaMigrator(
                this.logger,
                MysqlSchema.IP_HISTORY_COMPONENT,
                MysqlSchema.IP_HISTORY_TABLES,
                DependencyVersions.PLAYER_IP_HISTORY_SCHEMA_VERSION,
                MysqlSchema::initializeIpHistory,
                IP_HISTORY_MIGRATIONS
        ).migrate(jdbi, this.namePrefix());
    }

    private void verifyServerVersion(Jdbi jdbi) {
        String reported = jdbi.withHandle(handle -> handle.createQuery("SELECT VERSION()").mapTo(String.class).one());
        MysqlServerVersion version = MysqlServerVersion.parse(reported);
        if (version != null && version.atLeast(MINIMUM_SERVER_VERSION)) {
            return;
        }
        super.logger.error(LogConstants.STORAGE_MYSQL_VERSION_UNSUPPORTED, reported, MINIMUM_SERVER_VERSION.toString());
        throw new IllegalStateException(
                "MySQL server version " + reported + " is not supported, MySQL " + MINIMUM_SERVER_VERSION + " or later is required"
        );
    }

    @NotNull
    @Override
    public DataStore<UUID, Home> homeStore() {
        return this.homeStore;
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
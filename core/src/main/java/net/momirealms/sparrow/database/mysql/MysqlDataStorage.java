package net.momirealms.sparrow.database.mysql;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.player.PlayerProfile;
import net.momirealms.sparrow.database.mysql.upgrade.MysqlSchemaMigration;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
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

    protected HikariDataSource pool;
    protected MysqlPlayerProfileStore profileStore;

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
            new MysqlSchemaMigrator(
                    super.logger,
                    MysqlSchema.DATA_COMPONENT,
                    MysqlSchema.DATA_TABLES,
                    DependencyVersions.DATA_SCHEMA_VERSION,
                    MysqlSchema::initializeData,
                    MIGRATIONS
            ).migrate(jdbi, this.namePrefix());
            this.pool = connected;
            this.profileStore = new MysqlPlayerProfileStore(jdbi, super.executor, this.namePrefix());
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
        super.logger.error(LogConstants.STORAGE_MYSQL_VERSION_UNSUPPORTED, reported, MINIMUM_SERVER_VERSION.toString());
        throw new IllegalStateException(
                "MySQL server version " + reported + " is not supported, MySQL " + MINIMUM_SERVER_VERSION + " or later is required"
        );
    }

    @NotNull
    @Override
    public DataStore<String, PlayerProfile> playerProfileStore() {
        return this.profileStore;
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        return this.profileStore.lookupUser(name);
    }

    @Override
    public void close() {
        if (this.pool != null) {
            this.pool.close();
        }
    }
}
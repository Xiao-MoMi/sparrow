package net.momirealms.sparrow.database;

import net.momirealms.sparrow.database.mariadb.MariaDbDataStorage;
import net.momirealms.sparrow.database.mysql.MysqlDataStorage;
import net.momirealms.sparrow.database.postgresql.PostgresDataStorage;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public abstract class DataStorage implements AutoCloseable {
    protected final PluginConfig.DatabaseOptions options;
    protected final Executor executor;
    protected final PluginLogger logger;

    protected DataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        this.options = options;
        this.executor = executor;
        this.logger = logger;
    }

    @NotNull
    public static DataStorage create(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        return switch (options.type()) {
            case MYSQL -> new MysqlDataStorage(options, executor, logger);
            case MARIADB -> new MariaDbDataStorage(options, executor, logger);
            case POSTGRESQL -> new PostgresDataStorage(options, executor, logger);
        };
    }

    public abstract void initialize();

    @NotNull
    public abstract PlayerProfileStore playerProfileStore();

    @NotNull
    public abstract PlayerNameHistoryStore playerNameHistoryStore();

    // 建表时使用的名称前缀
    @NotNull
    protected String namePrefix() {
        return this.options.tablePrefix();
    }

    @Override
    public abstract void close();
}

package net.momirealms.sparrow.database;

import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import org.jetbrains.annotations.NotNull;

public abstract class DatabaseManager implements AutoCloseable {
    protected final PluginConfig.DatabaseOptions options;

    protected DatabaseManager(@NotNull PluginConfig.DatabaseOptions options) {
        this.options = options;
    }

    @NotNull
    public static DatabaseManager create(@NotNull PluginConfig.DatabaseOptions options) {
        return switch (options.type()) {
            case MYSQL, MARIADB, POSTGRESQL -> new SqlDatabaseManager(options);
            case MONGODB -> new MongoDatabaseManager(options);
        };
    }

    public abstract void initialize();

    @NotNull
    public DatabaseType type() {
        return this.options.type();
    }

    /**
     * 返回后续建表或建集合时使用的名称前缀。
     *
     * @return 当前数据库配置的表或集合前缀
     */
    @NotNull
    public String namePrefix() {
        return switch (this.options.type()) {
            case MYSQL -> this.options.mysql().tablePrefix();
            case MARIADB -> this.options.mariadb().tablePrefix();
            case POSTGRESQL -> this.options.postgresql().tablePrefix();
            case MONGODB -> this.options.mongodb().collectionPrefix();
        };
    }

    @Override
    public abstract void close();
}

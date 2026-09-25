package net.momirealms.sparrow.database;

import net.momirealms.sparrow.database.mariadb.MariaDbDataStorage;
import net.momirealms.sparrow.database.mongo.MongoDataStorage;
import net.momirealms.sparrow.database.mysql.MysqlDataStorage;
import net.momirealms.sparrow.database.postgresql.PostgresDataStorage;
import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
            case MONGODB -> new MongoDataStorage(options, executor, logger);
        };
    }

    public abstract void initialize();

    @NotNull
    public abstract CompletableFuture<Optional<PlayerData>> loadPlayer(@NotNull UUID player);

    /**
     * 更新玩家名、登录时间和数据更新时间, 保留上次下线位置.
     *
     * @param player 玩家 UUID
     * @param name 玩家当前的名字
     * @param timestamp 登录事件的时间, 单位为 Unix 毫秒
     * @return 写入完成时结束的任务
     */
    @NotNull
    public abstract CompletableFuture<Void> saveLogin(@NotNull UUID player, @NotNull String name, long timestamp);

    /**
     * 更新下线时间、服务器和位置, 保留登录时间. 时间均为 Unix 毫秒.
     */
    @NotNull
    public abstract CompletableFuture<Void> saveLogout(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location);

    /**
     * 按名字精确查询玩家 UUID, 区分大小写. 多名玩家先后用过同一名字时返回最近出现的那一位.
     *
     * @param name 玩家名
     * @return 查询任务, 没有记录时结果为空
     */
    @NotNull
    public abstract CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name);

    /**
     * 按 UUID 查询玩家最近一次使用的名字.
     *
     * @param player 玩家 UUID
     * @return 查询任务, 没有记录时结果为空
     */
    @NotNull
    public abstract CompletableFuture<Optional<String>> lookupName(@NotNull UUID player);

    // 建表或建集合时使用的名称前缀
    @NotNull
    protected String namePrefix() {
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

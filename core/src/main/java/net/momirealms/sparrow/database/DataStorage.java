package net.momirealms.sparrow.database;

import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class DataStorage implements AutoCloseable {
    protected final PluginConfig.DatabaseOptions options;
    protected final Executor executor;

    protected DataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor) {
        this.options = options;
        this.executor = executor;
    }

    @NotNull
    public static DataStorage create(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor) {
        return switch (options.type()) {
            case MYSQL, MARIADB, POSTGRESQL -> new SqlDataStorage(options, executor);
            case MONGODB -> new MongoDataStorage(options, executor);
        };
    }

    public abstract void initialize();

    /**
     * 写入玩家当前的名字并刷新最后出现时间, 已有记录会被覆盖.
     *
     * @param player 玩家 UUID
     * @param name 玩家当前的名字
     * @return 写入完成时结束的任务
     */
    @NotNull
    public abstract CompletableFuture<Void> saveUser(@NotNull UUID player, @NotNull String name);

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

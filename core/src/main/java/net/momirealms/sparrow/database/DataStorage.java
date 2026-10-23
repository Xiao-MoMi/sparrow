package net.momirealms.sparrow.database;

import net.momirealms.sparrow.database.mariadb.MariaDbDataStorage;
import net.momirealms.sparrow.database.mongo.MongoDataStorage;
import net.momirealms.sparrow.database.mysql.MysqlDataStorage;
import net.momirealms.sparrow.database.postgresql.PostgresDataStorage;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.player.PlayerProfile;
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
    public abstract DataStore<UUID, String, PlayerProfile> playerProfileStore();

    @NotNull
    public abstract CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name);

    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return this.playerProfileStore().load(player).thenApply(entries -> {
            PlayerProfile profile = entries.get(PlayerProfile.ENTRY);
            return profile == null ? Optional.empty() : Optional.of(profile.name());
        });
    }

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

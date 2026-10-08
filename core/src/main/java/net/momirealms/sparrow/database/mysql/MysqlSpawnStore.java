package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.SqlSpawnStore;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class MysqlSpawnStore extends SqlSpawnStore {
    private final PluginLogger logger;
    private final String prefix;

    MysqlSpawnStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        super(jdbi, executor, "`" + prefix + "spawn`");
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    protected void migrate(@NotNull Jdbi jdbi) {
        new MysqlSchemaMigrator(
                this.logger,
                MysqlSchema.SPAWN_COMPONENT,
                MysqlSchema.SPAWN_TABLES,
                DependencyVersions.SPAWN_SCHEMA_VERSION,
                MysqlSchema::initializeSpawn,
                List.of()
        ).migrate(jdbi, this.prefix);
    }

    @NotNull
    @Override
    protected String saveSql() {
        return "INSERT INTO " + super.table + " (id, server, world, x, y, z, yaw, pitch) "
                + "VALUES (1, :server, :world, :x, :y, :z, :yaw, :pitch) ON DUPLICATE KEY UPDATE "
                + "server = :server, world = :world, x = :x, y = :y, z = :z, yaw = :yaw, pitch = :pitch";
    }
}
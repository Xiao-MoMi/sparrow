package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.SqlHomeStore;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class PostgresHomeStore extends SqlHomeStore {
    private final PluginLogger logger;
    private final String prefix;

    PostgresHomeStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        super(jdbi, executor, "\"" + prefix + "homes\"");
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    protected void migrate(@NotNull Jdbi jdbi) {
        new PostgresSchemaMigrator(this.logger, PostgresSchema.HOME_COMPONENT, PostgresSchema.HOME_TABLES, DependencyVersions.HOME_SCHEMA_VERSION, PostgresSchema::initializeHomes, List.of()).migrate(jdbi, this.prefix);
    }

    @Override
    @NotNull
    protected Object uuidValue(@NotNull UUID uuid) {
        return uuid;
    }

    @Override
    protected boolean duplicateName(@NotNull SQLException exception) {
        return "23505".equals(exception.getSQLState());
    }

    @Override
    @NotNull
    protected UUID readUuid(@NotNull ResultSet result, @NotNull String column) throws SQLException {
        return result.getObject(column, UUID.class);
    }
}

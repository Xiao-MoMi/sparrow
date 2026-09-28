package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.SqlBanStore;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class PostgresBanStore extends SqlBanStore {
    private final PluginLogger logger;
    private final String prefix;

    PostgresBanStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        super(jdbi, executor, "\"" + prefix + "bans\"");
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    protected void migrate(@NotNull Jdbi jdbi) {
        new PostgresSchemaMigrator(this.logger, PostgresSchema.BAN_COMPONENT, PostgresSchema.BAN_TABLES, DependencyVersions.BAN_SCHEMA_VERSION, PostgresSchema::initializeBans, List.of()).migrate(jdbi, this.prefix);
    }

    @Override
    @NotNull
    protected Object uuidValue(@NotNull UUID uuid) {
        return uuid;
    }

    @Override
    protected int uuidNullType() {
        return Types.OTHER;
    }

    @Override
    @Nullable
    protected UUID readUuid(@NotNull ResultSet result, @NotNull String column) throws SQLException {
        return result.getObject(column, UUID.class);
    }
}

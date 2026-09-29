package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.SqlWarpStore;
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
public final class PostgresWarpStore extends SqlWarpStore {
    private final PluginLogger logger;
    private final String prefix;

    PostgresWarpStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        super(jdbi, executor, "\"" + prefix + "warps\"");
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    protected void migrate(@NotNull Jdbi jdbi) {
        new PostgresSchemaMigrator(this.logger, PostgresSchema.WARP_COMPONENT, PostgresSchema.WARP_TABLES, DependencyVersions.WARP_SCHEMA_VERSION, PostgresSchema::initializeWarps, List.of()).migrate(jdbi, this.prefix);
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

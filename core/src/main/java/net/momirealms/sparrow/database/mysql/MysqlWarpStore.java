package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.SqlWarpStore;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.UUIDUtils;
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

// MySQL 与 MariaDB 共用, UUID 存为 BINARY(16)
@ApiStatus.Internal
public final class MysqlWarpStore extends SqlWarpStore {
    private final PluginLogger logger;
    private final String prefix;

    MysqlWarpStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        super(jdbi, executor, "`" + prefix + "warps`");
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    protected void migrate(@NotNull Jdbi jdbi) {
        new MysqlSchemaMigrator(this.logger, MysqlSchema.WARP_COMPONENT, MysqlSchema.WARP_TABLES, DependencyVersions.WARP_SCHEMA_VERSION, MysqlSchema::initializeWarps, List.of())
                .migrate(jdbi, this.prefix);
    }

    @Override
    @NotNull
    protected Object uuidValue(@NotNull UUID uuid) {
        return UUIDUtils.toBytes(uuid);
    }

    @Override
    protected int uuidNullType() {
        return Types.BINARY;
    }

    @Override
    @Nullable
    protected UUID readUuid(@NotNull ResultSet result, @NotNull String column) throws SQLException {
        byte[] bytes = result.getBytes(column);
        return bytes == null ? null : UUIDUtils.fromBytes(bytes);
    }
}

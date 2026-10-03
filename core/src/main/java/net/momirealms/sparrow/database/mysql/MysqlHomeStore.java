package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.SqlHomeStore;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.UUIDUtils;
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
public final class MysqlHomeStore extends SqlHomeStore {
    private final PluginLogger logger;
    private final String prefix;

    MysqlHomeStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        super(jdbi, executor, "`" + prefix + "homes`");
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    protected void migrate(@NotNull Jdbi jdbi) {
        new MysqlSchemaMigrator(this.logger, MysqlSchema.HOME_COMPONENT, MysqlSchema.HOME_TABLES, DependencyVersions.HOME_SCHEMA_VERSION, MysqlSchema::initializeHomes, List.of())
                .migrate(jdbi, this.prefix);
    }

    @Override
    @NotNull
    protected Object uuidValue(@NotNull UUID uuid) {
        return UUIDUtils.toBytes(uuid);
    }

    @Override
    protected boolean duplicateName(@NotNull SQLException exception) {
        return exception.getErrorCode() == 1062;
    }

    @Override
    @NotNull
    protected UUID readUuid(@NotNull ResultSet result, @NotNull String column) throws SQLException {
        return UUIDUtils.fromBytes(result.getBytes(column));
    }
}

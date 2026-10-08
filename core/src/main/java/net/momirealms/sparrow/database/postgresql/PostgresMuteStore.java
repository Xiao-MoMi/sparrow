package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.SqlMuteStore;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class PostgresMuteStore extends SqlMuteStore {
    private final PluginLogger logger;
    private final String prefix;

    PostgresMuteStore(@NotNull Supplier<Jdbi> source, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        super(source, executor, "\"" + prefix + "mutes\"");
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    protected void migrate(@NotNull Jdbi jdbi) {
        new PostgresSchemaMigrator(
                this.logger,
                PostgresSchema.MUTE_COMPONENT,
                PostgresSchema.MUTE_TABLES,
                DependencyVersions.MUTE_SCHEMA_VERSION,
                PostgresSchema::initializeMutes,
                List.of()
        ).migrate(jdbi, this.prefix);
    }

    @Override
    @NotNull
    protected Object uuidValue(@NotNull UUID player) {
        return player;
    }

    @Override
    @NotNull
    protected UUID readUuid(@NotNull ResultSet row) throws SQLException {
        return row.getObject("player", UUID.class);
    }
}
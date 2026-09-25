package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PostgresSchema {
    public static final int CURRENT_VERSION = DependencyVersions.POSTGRESQL_SCHEMA_VERSION;

    private PostgresSchema() {
    }

    public static void initialize(@NotNull Handle handle, @NotNull String prefix) {
        String data = "\"" + prefix + "data\"";
        // 使用 PostgreSQL 原生 UUID, 名字以 C 排序规则精确比较.
        handle.execute("CREATE TABLE IF NOT EXISTS " + data + " ("
                + "player UUID PRIMARY KEY, name VARCHAR(64) COLLATE \"C\" NOT NULL, "
                + "last_login BIGINT NOT NULL DEFAULT 0, last_logout BIGINT NOT NULL DEFAULT 0, "
                + "last_server VARCHAR(255), last_location JSONB, updated_at BIGINT NOT NULL)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "data_name_updated\" ON " + data + " (name, updated_at, player)");
    }
}

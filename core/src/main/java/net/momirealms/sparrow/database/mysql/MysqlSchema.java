package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class MysqlSchema {
    public static final int CURRENT_VERSION = DependencyVersions.MYSQL_SCHEMA_VERSION;
    private static final String TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";

    private MysqlSchema() {
    }

    public static void initialize(@NotNull Handle handle, @NotNull String prefix) {
        // UUID 保存为固定 16 字节, 名字按 utf8mb4_bin 区分大小写.
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "data` ("
                + "player BINARY(16) PRIMARY KEY, name VARCHAR(64) NOT NULL, "
                + "last_login BIGINT NOT NULL DEFAULT 0, last_logout BIGINT NOT NULL DEFAULT 0, "
                + "last_server VARCHAR(255), last_location JSON, updated_at BIGINT NOT NULL, KEY data_name_updated (name, updated_at))" + TABLE_OPTIONS);
    }
}

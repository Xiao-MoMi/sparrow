package net.momirealms.sparrow.database.mysql;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class MysqlSchema {
    public static final String DATA_COMPONENT = "schema";
    public static final List<String> DATA_TABLES = List.of("data");

    private static final String TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";

    private MysqlSchema() {
    }

    public static void initializeData(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "data` ("
                + "uuid BINARY(16) PRIMARY KEY, name VARCHAR(64) NOT NULL, "
                + "login_at BIGINT NOT NULL, logout_at BIGINT NOT NULL DEFAULT 0, "
                + "ip VARBINARY(16), logout_location BLOB)" + TABLE_OPTIONS);
    }
}

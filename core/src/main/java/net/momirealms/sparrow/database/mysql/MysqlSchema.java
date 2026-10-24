package net.momirealms.sparrow.database.mysql;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class MysqlSchema {
    public static final String DATA_COMPONENT = "schema";
    public static final List<String> DATA_TABLES = List.of("data", "player_name_history", "player_ip_history");

    private static final String TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";

    private MysqlSchema() {
    }

    public static void initializeData(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "data` ("
                + "uuid BINARY(16) PRIMARY KEY, name VARCHAR(64) NOT NULL, "
                + "login_at BIGINT NOT NULL, logout_at BIGINT NOT NULL DEFAULT 0, "
                + "ip VARBINARY(16), logout_location BLOB)" + TABLE_OPTIONS);

        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "player_name_history` ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid BINARY(16) NOT NULL, "
                + "name VARCHAR(64) NOT NULL, name_key VARCHAR(64) NOT NULL, observed_at BIGINT NOT NULL, "
                + "KEY uuid_history (uuid, id), KEY name_history (name_key, id))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");

        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "player_ip_history` ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid BINARY(16) NOT NULL, "
                + "ip VARBINARY(16) NOT NULL, observed_at BIGINT NOT NULL, "
                + "KEY uuid_history (uuid, id), KEY ip_history (ip, id))" + TABLE_OPTIONS);
    }
}

package net.momirealms.sparrow.database.mysql;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class MysqlSchema {
    public static final String DATA_COMPONENT = "schema";
    public static final List<String> DATA_TABLES = List.of("data");

    public static final String NAME_HISTORY_COMPONENT = "player_name_history";
    public static final List<String> NAME_HISTORY_TABLES = List.of("player_name_history");

    public static final String IP_HISTORY_COMPONENT = "player_ip_history";
    public static final List<String> IP_HISTORY_TABLES = List.of("player_ip_history");

    public static final String BAN_COMPONENT = "ban";
    public static final List<String> BAN_TABLES = List.of("bans");

    public static final String HOME_COMPONENT = "home_schema";
    public static final List<String> HOME_TABLES = List.of("homes");

    public static final String WARP_COMPONENT = "warp_schema";
    public static final List<String> WARP_TABLES = List.of("warps");

    public static final String BACK_COMPONENT = "back";
    public static final List<String> BACK_TABLES = List.of("back");

    private static final String TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";

    private MysqlSchema() {
    }

    public static void initializeBans(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "bans` ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, active_key VARCHAR(64), player BINARY(16), player_name VARCHAR(64), "
                + "ip_start BIGINT, ip_end BIGINT, reason VARCHAR(256) NOT NULL, operator_name VARCHAR(64) NOT NULL, server VARCHAR(255) NOT NULL, "
                + "created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL DEFAULT 0, revoked_at BIGINT NOT NULL DEFAULT 0, revoked_by VARCHAR(64), "
                + "UNIQUE KEY bans_active_target (active_key), KEY bans_player (player, revoked_at), KEY bans_ip (ip_start, ip_end), KEY bans_created (created_at, id), "
                + "CHECK ((player IS NOT NULL AND ip_start IS NULL AND ip_end IS NULL) OR (player IS NULL AND ip_start IS NOT NULL AND ip_end IS NOT NULL)))" + TABLE_OPTIONS);
    }

    public static void initializeHomes(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "homes` ("
                + "id BINARY(16) PRIMARY KEY, owner BINARY(16) NOT NULL, name_key VARCHAR(64) NOT NULL, name VARCHAR(32) NOT NULL, "
                + "server VARCHAR(255) NOT NULL, world VARCHAR(255) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
                + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, "
                + "UNIQUE KEY homes_owner_name (owner, name_key))" + TABLE_OPTIONS);
    }

    public static void initializeWarps(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "warps` ("
                + "id BINARY(16) PRIMARY KEY, name_key VARCHAR(64) NOT NULL, name VARCHAR(32) NOT NULL, "
                + "server VARCHAR(255) NOT NULL, world VARCHAR(255) NOT NULL, x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
                + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, creator BINARY(16), created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, "
                + "UNIQUE KEY warps_name (name_key))" + TABLE_OPTIONS);
    }

    public static void initializeBack(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "back` ("
                + "uuid BINARY(16) PRIMARY KEY, back_location BLOB, death_location BLOB)" + TABLE_OPTIONS);
    }

    public static void initializeData(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "data` ("
                + "uuid BINARY(16) PRIMARY KEY, name VARCHAR(64) NOT NULL, "
                + "login_at BIGINT NOT NULL, logout_at BIGINT NOT NULL DEFAULT 0, "
                + "ip VARBINARY(16), logout_location BLOB)" + TABLE_OPTIONS);
    }

    public static void initializeNameHistory(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "player_name_history` ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid BINARY(16) NOT NULL, "
                + "name VARCHAR(64) NOT NULL, name_key VARCHAR(64) NOT NULL, observed_at BIGINT NOT NULL, "
                + "KEY uuid_history (uuid, id), KEY name_history (name_key, id))"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
    }

    public static void initializeIpHistory(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "player_ip_history` ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, uuid BINARY(16) NOT NULL, "
                + "ip VARBINARY(16) NOT NULL, observed_at BIGINT NOT NULL, "
                + "KEY uuid_history (uuid, id), KEY ip_history (ip, id))" + TABLE_OPTIONS);
    }
}

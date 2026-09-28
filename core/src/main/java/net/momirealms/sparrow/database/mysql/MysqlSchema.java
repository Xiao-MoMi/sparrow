package net.momirealms.sparrow.database.mysql;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class MysqlSchema {
    public static final String DATA_COMPONENT = "schema";
    public static final List<String> DATA_TABLES = List.of("data");

    // 封禁模块的表单独记录版本, 模块启用后才创建
    public static final String BAN_COMPONENT = "ban_schema";
    public static final List<String> BAN_TABLES = List.of("bans");

    private static final String TABLE_OPTIONS = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin";

    private MysqlSchema() {
    }

    public static void initializeData(@NotNull Handle handle, @NotNull String prefix) {
        // UUID 保存为固定 16 字节, 名字按 utf8mb4_bin 区分大小写.
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "data` ("
                + "player BINARY(16) PRIMARY KEY, name VARCHAR(64) NOT NULL, "
                + "last_login BIGINT NOT NULL DEFAULT 0, last_logout BIGINT NOT NULL DEFAULT 0, "
                + "last_logout_server VARCHAR(255), last_logout_location JSON, last_login_ip BIGINT, updated_at BIGINT NOT NULL, "
                + "KEY data_name_updated (name, updated_at), KEY data_login_ip (last_login_ip))" + TABLE_OPTIONS);
    }

    // player 与 ip_start/ip_end 至少有一组, 两组都有时为账号加 IP 的封禁. IPv4 按无符号整数存为 BIGINT
    public static void initializeBans(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS `" + prefix + "bans` ("
                + "id CHAR(8) PRIMARY KEY, player BINARY(16), player_name VARCHAR(64), ip_start BIGINT, ip_end BIGINT, "
                + "reason VARCHAR(256) NOT NULL, operator_name VARCHAR(64) NOT NULL, server VARCHAR(255) NOT NULL, "
                + "created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL DEFAULT 0, revoked_at BIGINT NOT NULL DEFAULT 0, revoked_by VARCHAR(64), "
                + "KEY bans_player (player, revoked_at), KEY bans_ip (ip_start, ip_end), KEY bans_created (created_at))" + TABLE_OPTIONS);
    }
}

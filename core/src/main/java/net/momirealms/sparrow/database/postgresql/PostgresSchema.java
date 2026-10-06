package net.momirealms.sparrow.database.postgresql;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class PostgresSchema {
    private static final String CREATE_INDEX_PREFIX = "CREATE INDEX IF NOT EXISTS \"";

    public static final String DATA_COMPONENT = "schema";
    public static final List<String> DATA_TABLES = List.of("data");

    // 封禁模块
    public static final String BAN_COMPONENT = "ban_schema";
    public static final List<String> BAN_TABLES = List.of("bans");

    private PostgresSchema() {
    }

    public static void initializeData(@NotNull Handle handle, @NotNull String prefix) {
        String data = "\"" + prefix + "data\"";
        // 使用 PostgreSQL 原生 UUID, 名字以 C 排序规则精确比较.
        handle.execute("CREATE TABLE IF NOT EXISTS " + data + " ("
                + "player UUID PRIMARY KEY, name VARCHAR(64) COLLATE \"C\" NOT NULL, "
                + "last_login BIGINT NOT NULL DEFAULT 0, last_logout BIGINT NOT NULL DEFAULT 0, "
                + "last_logout_server VARCHAR(255), last_logout_location JSONB, last_login_ip BIGINT, updated_at BIGINT NOT NULL)");
        handle.execute(CREATE_INDEX_PREFIX + prefix + "data_name_updated\" ON " + data + " (name, updated_at, player)");
        handle.execute(CREATE_INDEX_PREFIX + prefix + "data_login_ip\" ON " + data + " (last_login_ip)");
    }

    // player 与 ip_start/ip_end 至少有一组, 两组都有时为账号加 IP 的封禁. IPv4 按无符号整数存为 BIGINT
    public static void initializeBans(@NotNull Handle handle, @NotNull String prefix) {
        String bans = "\"" + prefix + "bans\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + bans + " ("
                + "id CHAR(8) PRIMARY KEY, player UUID, player_name VARCHAR(64), ip_start BIGINT, ip_end BIGINT, "
                + "reason VARCHAR(256) NOT NULL, operator_name VARCHAR(64) NOT NULL, server VARCHAR(255) NOT NULL, "
                + "created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL DEFAULT 0, revoked_at BIGINT NOT NULL DEFAULT 0, revoked_by VARCHAR(64))");
        handle.execute(CREATE_INDEX_PREFIX + prefix + "bans_player\" ON " + bans + " (player, revoked_at)");
        handle.execute(CREATE_INDEX_PREFIX + prefix + "bans_ip\" ON " + bans + " (ip_start, ip_end)");
        handle.execute(CREATE_INDEX_PREFIX + prefix + "bans_operator\" ON " + bans + " (LOWER(operator_name))");
        handle.execute(CREATE_INDEX_PREFIX + prefix + "bans_created\" ON " + bans + " (created_at)");
    }
}
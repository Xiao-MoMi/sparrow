package net.momirealms.sparrow.database.postgresql;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class PostgresSchema {
    public static final String DATA_COMPONENT = "schema";
    public static final List<String> DATA_TABLES = List.of("data");

    // 封禁模块
    public static final String BAN_COMPONENT = "ban_schema";
    public static final List<String> BAN_TABLES = List.of("bans");

    // warp 模块
    public static final String WARP_COMPONENT = "warp_schema";
    public static final List<String> WARP_TABLES = List.of("warps");

    // home 模块
    public static final String HOME_COMPONENT = "home_schema";
    public static final List<String> HOME_TABLES = List.of("homes");

    // spawn 模块
    public static final String SPAWN_COMPONENT = "spawn_schema";
    public static final List<String> SPAWN_TABLES = List.of("spawn");

    private PostgresSchema() {
    }

    public static void initializeData(@NotNull Handle handle, @NotNull String prefix) {
        String data = "\"" + prefix + "data\"";
        // 使用 PostgreSQL 原生 UUID, 名字以 C 排序规则精确比较.
        handle.execute("CREATE TABLE IF NOT EXISTS " + data + " ("
                + "player UUID PRIMARY KEY, name VARCHAR(64) COLLATE \"C\" NOT NULL, "
                + "last_login BIGINT NOT NULL DEFAULT 0, last_logout BIGINT NOT NULL DEFAULT 0, "
                + "last_logout_server VARCHAR(255), last_logout_location JSONB, last_login_ip BIGINT, updated_at BIGINT NOT NULL)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "data_name_updated\" ON " + data + " (name, updated_at, player)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "data_login_ip\" ON " + data + " (last_login_ip)");
    }

    // player 与 ip_start/ip_end 至少有一组, 两组都有时为账号加 IP 的封禁. IPv4 按无符号整数存为 BIGINT
    public static void initializeBans(@NotNull Handle handle, @NotNull String prefix) {
        String bans = "\"" + prefix + "bans\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + bans + " ("
                + "id CHAR(8) PRIMARY KEY, player UUID, player_name VARCHAR(64), ip_start BIGINT, ip_end BIGINT, "
                + "reason VARCHAR(256) NOT NULL, operator_name VARCHAR(64) NOT NULL, server VARCHAR(255) NOT NULL, "
                + "created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL DEFAULT 0, revoked_at BIGINT NOT NULL DEFAULT 0, revoked_by VARCHAR(64))");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "bans_player\" ON " + bans + " (player, revoked_at)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "bans_ip\" ON " + bans + " (ip_start, ip_end)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "bans_operator\" ON " + bans + " (LOWER(operator_name))");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "bans_created\" ON " + bans + " (created_at)");
    }

    // name_key 是小写后的名称, 个别字符转小写后会变长, 所以比 name 宽
    public static void initializeWarps(@NotNull Handle handle, @NotNull String prefix) {
        String warps = "\"" + prefix + "warps\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + warps + " ("
                + "id UUID PRIMARY KEY, name_key VARCHAR(64) COLLATE \"C\" NOT NULL, name VARCHAR(32) NOT NULL, description VARCHAR(256) NOT NULL, "
                + "server VARCHAR(255) NOT NULL, world VARCHAR(255) NOT NULL, x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, z DOUBLE PRECISION NOT NULL, "
                + "yaw REAL NOT NULL, pitch REAL NOT NULL, creator UUID, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)");
        handle.execute("CREATE UNIQUE INDEX IF NOT EXISTS \"" + prefix + "warps_name\" ON " + warps + " (name_key)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "warps_location\" ON " + warps + " (server, world)");
    }

    public static void initializeHomes(@NotNull Handle handle, @NotNull String prefix) {
        String homes = "\"" + prefix + "homes\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + homes + " ("
                + "id UUID PRIMARY KEY, owner UUID NOT NULL, name_key VARCHAR(64) COLLATE \"C\" NOT NULL, name VARCHAR(32) NOT NULL, "
                + "server VARCHAR(255) NOT NULL, world VARCHAR(255) NOT NULL, x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, z DOUBLE PRECISION NOT NULL, "
                + "yaw REAL NOT NULL, pitch REAL NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)");
        handle.execute("CREATE UNIQUE INDEX IF NOT EXISTS \"" + prefix + "homes_owner_name\" ON " + homes + " (owner, name_key)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "homes_location\" ON " + homes + " (server, world)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "homes_world\" ON " + homes + " (world)");
    }

    public static void initializeSpawn(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS \"" + prefix + "spawn\" ("
                + "id SMALLINT PRIMARY KEY, server VARCHAR(255) NOT NULL, world VARCHAR(255) NOT NULL, "
                + "x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, z DOUBLE PRECISION NOT NULL, "
                + "yaw REAL NOT NULL, pitch REAL NOT NULL)");
    }
}

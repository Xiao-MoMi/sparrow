package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.player.PlayerProfile;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class MysqlPlayerProfileStore implements DataStore<UUID, String, PlayerProfile> {
    private final Jdbi sql;
    private final Executor executor;
    private final String table;

    public MysqlPlayerProfileStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        this.sql = sql;
        this.executor = executor;
        this.table = "`" + prefix + "data`";
    }

    @NotNull
    @Override
    public CompletableFuture<Map<String, PlayerProfile>> load(@NotNull UUID key) {
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createQuery("SELECT name, login_at, logout_at, ip, logout_location FROM " + this.table + " WHERE uuid = :uuid")
                .bind("uuid", UUIDUtils.toBytes(key))
                .map((result, context) -> readProfile(result))
                .findOne()
                .map(profile -> Map.of(PlayerProfile.ENTRY, profile))
                .orElseGet(Map::of)), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> save(@NotNull UUID key, @NotNull Map<String, PlayerProfile> upserts, @NotNull Set<String> removals) {
        PlayerProfile profile = upserts.get(PlayerProfile.ENTRY);
        if (profile != null) {
            String statement = "INSERT INTO " + this.table + " (uuid, name, login_at, logout_at, ip, logout_location)"
                    + " VALUES (:uuid, :name, :login, :logout, :ip, :location)"
                    + " ON DUPLICATE KEY UPDATE name = :name, login_at = :login, logout_at = :logout, ip = :ip, logout_location = :location";
            return CompletableFuture.runAsync(() -> this.sql.useHandle(handle -> handle.createUpdate(statement)
                    .bind("uuid", UUIDUtils.toBytes(key))
                    .bind("name", profile.name())
                    .bind("login", profile.loginAt())
                    .bind("logout", profile.logoutAt())
                    .bind("ip", profile.ip() == null ? null : profile.ip().getAddress())
                    .bind("location", PlayerProfile.Codec.encodeLocation(profile.logoutLocation()))
                    .execute()), this.executor);
        }
        if (removals.contains(PlayerProfile.ENTRY)) {
            return CompletableFuture.runAsync(() -> this.sql.useHandle(handle -> handle.createUpdate("DELETE FROM " + this.table + " WHERE uuid = :uuid")
                    .bind("uuid", UUIDUtils.toBytes(key))
                    .execute()), this.executor);
        }
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        // 二进制比较保留尾随空格的区别, 与其他数据库的精确匹配一致
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createQuery("SELECT uuid FROM " + this.table
                        + " WHERE CAST(name AS BINARY) = CAST(:name AS BINARY) ORDER BY login_at DESC, uuid DESC LIMIT 1")
                .bind("name", name)
                .map((result, context) -> UUIDUtils.fromBytes(result.getBytes("uuid")))
                .findOne()), this.executor);
    }

    private static PlayerProfile readProfile(ResultSet result) throws SQLException {
        return new PlayerProfile(
                result.getString("name"),
                result.getLong("login_at"),
                result.getLong("logout_at"),
                PlayerProfile.Codec.decodeAddress(result.getBytes("ip")),
                PlayerProfile.Codec.decodeLocation(result.getBytes("logout_location"))
        );
    }
}
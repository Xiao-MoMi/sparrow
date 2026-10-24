package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.PlayerProfileStore;
import net.momirealms.sparrow.database.model.PlayerProfile;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.NBT;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class MysqlPlayerProfileStore implements PlayerProfileStore {
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
    public CompletableFuture<Optional<PlayerProfile>> load(@NotNull UUID key) {
        String query = "SELECT name, login_at, logout_at, ip, logout_location FROM " + this.table + " WHERE uuid = :uuid";
        return CompletableFuture.supplyAsync(
                () -> this.sql.withHandle(handle -> handle.createQuery(query)
                        .bind("uuid", UUIDUtils.toBytes(key))
                        .map((result, context) -> readProfile(result))
                        .findOne()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> saveLogin(@NotNull UUID player, @NotNull String name, long loginAt, @Nullable InetAddress ip) {
        String statement = "INSERT INTO " + this.table + " (uuid, name, login_at, ip) VALUES (:uuid, :name, :login, :ip)"
                + " ON DUPLICATE KEY UPDATE name = :name, login_at = :login, ip = :ip";
        return CompletableFuture.runAsync(
                () -> this.sql.useHandle(handle -> handle.createUpdate(statement)
                        .bind("uuid", UUIDUtils.toBytes(player))
                        .bind("name", name)
                        .bind("login", loginAt)
                        .bind("ip", ip == null ? null : ip.getAddress())
                        .execute()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> saveLogout(@NotNull UUID player, long logoutAt, @NotNull ServerLocation location) {
        String statement = "UPDATE " + this.table + " SET logout_at = :logout, logout_location = :location WHERE uuid = :uuid";
        return CompletableFuture.runAsync(
                () -> this.sql.useHandle(handle -> handle.createUpdate(statement)
                        .bind("uuid", UUIDUtils.toBytes(player))
                        .bind("logout", logoutAt)
                        .bind("location", encodeLocation(location))
                        .execute()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        // 二进制比较保留尾随空格的区别, 与其他数据库的精确匹配一致
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createQuery("SELECT uuid FROM " + this.table
                        + " WHERE CAST(name AS BINARY) = CAST(:name AS BINARY) ORDER BY login_at DESC, uuid DESC LIMIT 1")
                .bind("name", name)
                .map((result, context) -> UUIDUtils.fromBytes(result.getBytes("uuid")))
                .findOne()), this.executor);
    }

    @NotNull
    private static PlayerProfile readProfile(@NotNull ResultSet result) throws SQLException {
        return new PlayerProfile(
                result.getString("name"),
                result.getLong("login_at"),
                result.getLong("logout_at"),
                decodeAddress(result.getBytes("ip")),
                decodeLocation(result.getBytes("logout_location"))
        );
    }

    @Nullable
    private static InetAddress decodeAddress(byte @Nullable [] bytes) {
        if (bytes == null) return null;
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Player profile IP requires 4 or 16 bytes", exception);
        }
    }

    @NotNull
    private static byte[] encodeLocation(@NotNull ServerLocation location) {
        try {
            return NBT.toBytes(location.toNbt());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Nullable
    private static ServerLocation decodeLocation(byte @Nullable [] bytes) {
        if (bytes == null) return null;
        try {
            CompoundTag tag = NBT.fromBytes(bytes);
            if (tag == null) throw new IllegalArgumentException("Player profile location must contain a compound");
            return ServerLocation.fromNbt(tag);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
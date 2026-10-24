package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.PlayerProfileStore;
import net.momirealms.sparrow.player.PlayerProfile;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.NBT;
import net.momirealms.sparrow.util.ServerLocation;
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
public final class PostgresPlayerProfileStore implements PlayerProfileStore {
    private final Jdbi sql;
    private final Executor executor;
    private final String table;

    public PostgresPlayerProfileStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        this.sql = sql;
        this.executor = executor;
        this.table = "\"" + prefix + "data\"";
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<PlayerProfile>> load(@NotNull UUID key) {
        String query = "SELECT name, login_at, logout_at, ip, logout_location FROM " + this.table + " WHERE uuid = :uuid";
        return CompletableFuture.supplyAsync(
                () -> this.sql.withHandle(handle -> handle.createQuery(query)
                        .bind("uuid", key)
                        .map((result, context) -> readProfile(result))
                        .findOne()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> save(@NotNull UUID key, @NotNull PlayerProfile profile) {
        String statement = "INSERT INTO " + this.table + " (uuid, name, login_at, logout_at, ip, logout_location)"
                + " VALUES (:uuid, :name, :login, :logout, :ip, :location)"
                + " ON CONFLICT (uuid) DO UPDATE SET name = EXCLUDED.name, login_at = EXCLUDED.login_at, logout_at = EXCLUDED.logout_at,"
                + " ip = EXCLUDED.ip, logout_location = EXCLUDED.logout_location";
        return CompletableFuture.runAsync(
                () -> this.sql.useHandle(handle -> handle.createUpdate(statement)
                        .bind("uuid", key)
                        .bind("name", profile.name())
                        .bind("login", profile.loginAt())
                        .bind("logout", profile.logoutAt())
                        .bind("ip", profile.ip() == null ? null : profile.ip().getAddress())
                        .bind("location", encodeLocation(profile.logoutLocation()))
                        .execute()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createQuery("SELECT uuid FROM " + this.table
                        + " WHERE name = :name ORDER BY login_at DESC, uuid DESC LIMIT 1")
                .bind("name", name)
                .map((result, context) -> result.getObject("uuid", UUID.class))
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

    private static byte @Nullable [] encodeLocation(@Nullable ServerLocation location) {
        if (location == null) return null;
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
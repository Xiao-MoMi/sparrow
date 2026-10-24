package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.PlayerIpHistoryStore;
import net.momirealms.sparrow.database.model.PlayerIpRecord;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class PostgresPlayerIpHistoryStore implements PlayerIpHistoryStore {
    private final Jdbi sql;
    private final Executor executor;
    private final String table;

    public PostgresPlayerIpHistoryStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        this.sql = sql;
        this.executor = executor;
        this.table = '"' + prefix + "player_ip_history" + '"';
    }

    @NotNull
    @Override
    public CompletableFuture<Void> recordIfChanged(@NotNull UUID uuid, @NotNull InetAddress ip, long observedAt) {
        byte[] address = ip.getAddress();
        return CompletableFuture.runAsync(
                () -> this.sql.useTransaction(handle -> {
                    Optional<byte[]> previous = handle.createQuery("SELECT ip FROM " + this.table + " WHERE uuid = :uuid ORDER BY id DESC LIMIT 1")
                            .bind("uuid", uuid)
                            .map((result, context) -> result.getBytes("ip"))
                            .findOne();
                    if (previous.isPresent() && Arrays.equals(previous.get(), address)) return;
                    handle.createUpdate("INSERT INTO " + this.table + " (uuid, ip, observed_at) VALUES (:uuid, :ip, :observed_at)")
                            .bind("uuid", uuid)
                            .bind("ip", address)
                            .bind("observed_at", observedAt)
                            .execute();
                }),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<PlayerIpRecord>> findLatestByUuid(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(
                () -> this.sql.withHandle(handle -> handle.createQuery("SELECT id, uuid, ip, observed_at FROM " + this.table + " WHERE uuid = :uuid ORDER BY id DESC LIMIT 1")
                        .bind("uuid", uuid)
                        .map((result, context) -> readRecord(result))
                        .findOne()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Long> countAddresses(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(
                () -> this.sql.withHandle(handle -> handle.createQuery("SELECT COUNT(DISTINCT ip) FROM " + this.table + " WHERE uuid = :uuid")
                        .bind("uuid", uuid)
                        .mapTo(Long.class)
                        .one()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<List<PlayerIpRecord>> listAddresses(@NotNull UUID uuid, int offset, int limit) {
        String query = "SELECT h.id, h.uuid, h.ip, h.observed_at FROM " + this.table + " h"
                + " JOIN (SELECT MAX(id) AS id FROM " + this.table + " WHERE uuid = :uuid GROUP BY ip) latest ON latest.id = h.id"
                + " ORDER BY h.id DESC LIMIT :limit OFFSET :offset";
        return CompletableFuture.supplyAsync(
                () -> this.sql.withHandle(handle -> handle.createQuery(query)
                        .bind("uuid", uuid)
                        .bind("offset", offset)
                        .bind("limit", limit)
                        .map((result, context) -> readRecord(result))
                        .list()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Long> countPlayers(@NotNull InetAddress ip) {
        byte[] address = ip.getAddress();
        return CompletableFuture.supplyAsync(
                () -> this.sql.withHandle(handle -> handle.createQuery("SELECT COUNT(DISTINCT uuid) FROM " + this.table + " WHERE ip = :ip")
                        .bind("ip", address)
                        .mapTo(Long.class)
                        .one()),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<List<PlayerIpRecord>> listPlayers(@NotNull InetAddress ip, int offset, int limit) {
        byte[] address = ip.getAddress();
        String query = "SELECT h.id, h.uuid, h.ip, h.observed_at FROM " + this.table + " h"
                + " JOIN (SELECT MAX(id) AS id FROM " + this.table + " WHERE ip = :ip GROUP BY uuid) latest ON latest.id = h.id"
                + " ORDER BY h.id DESC LIMIT :limit OFFSET :offset";
        return CompletableFuture.supplyAsync(
                () -> this.sql.withHandle(handle -> handle.createQuery(query)
                        .bind("ip", address)
                        .bind("offset", offset)
                        .bind("limit", limit)
                        .map((result, context) -> readRecord(result))
                        .list()),
                this.executor
        );
    }

    @NotNull
    private static PlayerIpRecord readRecord(@NotNull ResultSet result) throws SQLException {
        return new PlayerIpRecord(result.getLong("id"), result.getObject("uuid", UUID.class), decodeAddress(result.getBytes("ip")), result.getLong("observed_at"));
    }

    @NotNull
    private static InetAddress decodeAddress(byte @NotNull [] bytes) {
        try {
            return bytes.length == 16 ? Inet6Address.getByAddress(null, bytes, -1) : InetAddress.getByAddress(bytes);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Player IP history requires 4 or 16 address bytes", exception);
        }
    }
}
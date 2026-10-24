package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.PlayerNameHistoryStore;
import net.momirealms.sparrow.database.model.PlayerNameRecord;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class PostgresPlayerNameHistoryStore implements PlayerNameHistoryStore {
    private final Jdbi sql;
    private final Executor executor;
    private final String table;

    public PostgresPlayerNameHistoryStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        this.sql = sql;
        this.executor = executor;
        this.table = '"' + prefix + "player_name_history" + '"';
    }

    @NotNull
    @Override
    public CompletableFuture<Void> recordIfChanged(@NotNull UUID uuid, @NotNull String name, long observedAt) {
        String nameKey = name.toLowerCase(Locale.ROOT);
        return CompletableFuture.runAsync(
                () -> this.sql.useTransaction(handle -> {
                    Optional<PlayerNameRecord> byUuid = this.findLatestByUuid(handle, uuid);
                    Optional<PlayerNameRecord> byName = this.findLatestByName(handle, nameKey);
                    if (byUuid.isPresent() && byUuid.get().name().equals(name) && byName.isPresent() && byName.get().uuid().equals(uuid)) return;
                    handle.createUpdate("INSERT INTO " + this.table + " (uuid, name, name_key, observed_at) VALUES (:uuid, :name, :name_key, :observed_at)")
                            .bind("uuid", uuid)
                            .bind("name", name)
                            .bind("name_key", nameKey)
                            .bind("observed_at", observedAt)
                            .execute();
                }),
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<PlayerNameRecord>> findLatestByName(@NotNull String name) {
        String nameKey = name.toLowerCase(Locale.ROOT);
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> this.findLatestByName(handle, nameKey)), this.executor);
    }

    @NotNull
    private Optional<PlayerNameRecord> findLatestByName(@NotNull Handle handle, @NotNull String nameKey) {
        String query = "SELECT id, uuid, name, observed_at FROM " + this.table
                + " WHERE name_key = :name_key ORDER BY id DESC LIMIT 1";
        return handle.createQuery(query)
                .bind("name_key", nameKey)
                .map((result, context) -> readRecord(result))
                .findOne();
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<PlayerNameRecord>> findLatestByUuid(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> this.findLatestByUuid(handle, uuid)), this.executor);
    }

    @NotNull
    private Optional<PlayerNameRecord> findLatestByUuid(@NotNull Handle handle, @NotNull UUID uuid) {
        return handle.createQuery("SELECT id, uuid, name, observed_at FROM " + this.table + " WHERE uuid = :uuid ORDER BY id DESC LIMIT 1")
                .bind("uuid", uuid)
                .map((result, context) -> readRecord(result))
                .findOne();
    }

    @NotNull
    @Override
    public CompletableFuture<List<PlayerNameRecord>> history(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(
                () -> this.sql.withHandle(handle -> handle.createQuery("SELECT id, uuid, name, observed_at FROM " + this.table + " WHERE uuid = :uuid ORDER BY id")
                        .bind("uuid", uuid)
                        .map((result, context) -> readRecord(result))
                        .list()),
                this.executor
        );
    }

    @NotNull
    private static PlayerNameRecord readRecord(@NotNull ResultSet result) throws SQLException {
        return new PlayerNameRecord(result.getLong("id"), result.getObject("uuid", UUID.class), result.getString("name"), result.getLong("observed_at"));
    }
}
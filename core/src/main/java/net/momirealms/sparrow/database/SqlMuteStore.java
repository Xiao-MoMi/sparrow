package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.mute.MuteRecord;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public abstract class SqlMuteStore implements MuteStore {
    private final Supplier<Jdbi> source;
    private final Executor executor;
    private final String table;
    private volatile boolean initialized;

    protected SqlMuteStore(@NotNull Supplier<Jdbi> source, @NotNull Executor executor, @NotNull String table) {
        this.source = source;
        this.executor = executor;
        this.table = table;
    }

    protected abstract void migrate(@NotNull Jdbi jdbi);

    @NotNull
    protected abstract Object uuidValue(@NotNull UUID player);

    @NotNull
    protected abstract UUID readUuid(@NotNull ResultSet row) throws SQLException;

    @NotNull
    private Jdbi sql() {
        Jdbi jdbi = this.source.get();
        if (!this.initialized) {
            synchronized (this) {
                if (!this.initialized) {
                    this.migrate(jdbi);
                    this.initialized = true;
                }
            }
        }
        return jdbi;
    }

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::sql, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<MuteRecord>> findActive(@NotNull UUID player, long now) {
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle
                .createQuery("SELECT * FROM " + this.table + " WHERE active_player = :player AND expires_at > :now")
                .bind("player", this.uuidValue(player))
                .bind("now", now)
                .map((row, context) -> this.read(row))
                .findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> create(@NotNull MuteRecord record) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.sql().inTransaction(handle -> {
                    boolean active = handle.createQuery("SELECT id FROM " + this.table + " WHERE active_player = :player AND expires_at > :now")
                            .bind("player", this.uuidValue(record.player()))
                            .bind("now", record.createdAt())
                            .mapTo(String.class)
                            .findOne()
                            .isPresent();
                    if (active) return false;
                    // 释放过期记录的唯一键, 历史记录继续保留.
                    handle.createUpdate("UPDATE " + this.table + " SET active_player = NULL WHERE active_player = :player AND expires_at <= :now")
                            .bind("player", this.uuidValue(record.player()))
                            .bind("now", record.createdAt())
                            .execute();
                    handle.createUpdate("INSERT INTO " + this.table + " (id, player, active_player, player_name, reason, operator_name, server, created_at, expires_at, revoked_at)"
                                    + " VALUES (:id, :player, :player, :name, :reason, :operator, :server, :created, :expires, 0)")
                            .bind("id", record.id())
                            .bind("player", this.uuidValue(record.player()))
                            .bind("name", record.playerName())
                            .bind("reason", record.reason())
                            .bind("operator", record.operatorName())
                            .bind("server", record.server())
                            .bind("created", record.createdAt())
                            .bind("expires", record.expiresAt())
                            .execute();
                    return true;
                });
            } catch (UnableToExecuteStatementException exception) {
                // 同一玩家的并发禁言由唯一键裁定, 只有写入成功的一条生效.
                if (exception.getCause() instanceof SQLException sql && ("23505".equals(sql.getSQLState()) || sql.getErrorCode() == 1062)) {
                    return false;
                }
                throw exception;
            }
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<MuteRecord>> revoke(@NotNull UUID player, long now, @NotNull String operator) {
        return CompletableFuture.supplyAsync(() -> this.sql().inTransaction(handle -> {
            Optional<MuteRecord> current = handle.createQuery("SELECT * FROM " + this.table + " WHERE active_player = :player AND expires_at > :now FOR UPDATE")
                    .bind("player", this.uuidValue(player))
                    .bind("now", now)
                    .map((row, context) -> this.read(row))
                    .findOne();
            if (current.isEmpty()) return Optional.empty();
            MuteRecord record = current.get();
            handle.createUpdate("UPDATE " + this.table + " SET active_player = NULL, revoked_at = :now, revoked_by = :operator WHERE id = :id")
                    .bind("now", now)
                    .bind("operator", operator)
                    .bind("id", record.id())
                    .execute();
            return Optional.of(record.revoke(now, operator));
        }), this.executor);
    }

    @NotNull
    private MuteRecord read(ResultSet row) throws SQLException {
        return new MuteRecord(
                row.getString("id"),
                this.readUuid(row),
                row.getString("player_name"),
                row.getString("reason"),
                row.getString("operator_name"),
                row.getString("server"),
                row.getLong("created_at"),
                row.getLong("expires_at"),
                row.getLong("revoked_at"),
                row.getString("revoked_by")
        );
    }
}
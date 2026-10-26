package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.util.WorldLocation;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class SqlWarpStore implements WarpStore {
    private static final String COLUMNS = "id, name, server, world, x, y, z, yaw, pitch, creator, created_at, updated_at";

    private final Jdbi sql;
    private final Executor executor;
    private final String table;

    protected SqlWarpStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String table) {
        this.sql = sql;
        this.executor = executor;
        this.table = table;
    }

    @NotNull
    @Override
    public CompletableFuture<List<Warp>> loadAll() {
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createQuery("SELECT " + COLUMNS + " FROM " + this.table)
                .map((row, context) -> this.read(row))
                .list()), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<Warp>> find(@NotNull UUID id) {
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createQuery("SELECT " + COLUMNS + " FROM " + this.table + " WHERE id = :id")
                .bind("id", this.encodeUuid(id))
                .map((row, context) -> this.read(row))
                .findOne()), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Status> create(@NotNull Warp warp) {
        String insert = "INSERT INTO " + this.table + " (id, name_key, name, server, world, x, y, z, yaw, pitch, creator, created_at, updated_at)"
                + " VALUES (:id, :name_key, :name, :server, :world, :x, :y, :z, :yaw, :pitch, :creator, :created_at, :updated_at)";
        return this.write(() -> this.sql.withHandle(handle -> this.bindLocation(handle.createUpdate(insert), warp.server(), warp.location())
                .bind("id", this.encodeUuid(warp.id()))
                .bind("name", warp.name())
                .bind("name_key", warp.getNameKey())
                .bindBySqlType("creator", warp.creator() == null ? null : this.encodeUuid(warp.creator()), this.uuidSqlType())
                .bind("created_at", warp.createdAt())
                .bind("updated_at", warp.updatedAt())
                .execute()));
    }

    @NotNull
    @Override
    public CompletableFuture<Status> rename(@NotNull UUID id, @NotNull String name, long updatedAt) {
        String update = "UPDATE " + this.table + " SET name = :name, name_key = :name_key, updated_at = :updated_at WHERE id = :id";
        return this.write(() -> this.sql.withHandle(handle -> handle.createUpdate(update)
                .bind("id", this.encodeUuid(id))
                .bind("name", name)
                .bind("name_key", Warp.key(name))
                .bind("updated_at", updatedAt)
                .execute()));
    }

    @NotNull
    @Override
    public CompletableFuture<Status> relocate(@NotNull UUID id, @NotNull String server, @NotNull WorldLocation location, long updatedAt) {
        String update = "UPDATE " + this.table + " SET server = :server, world = :world, x = :x, y = :y, z = :z, yaw = :yaw, pitch = :pitch,"
                + " updated_at = :updated_at WHERE id = :id";
        return this.write(() -> this.sql.withHandle(handle -> this.bindLocation(handle.createUpdate(update), server, location)
                .bind("id", this.encodeUuid(id))
                .bind("updated_at", updatedAt)
                .execute()));
    }

    private CompletableFuture<Status> write(Supplier<Integer> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.get() > 0 ? Status.SUCCESS : Status.NOT_FOUND;
            } catch (UnableToExecuteStatementException exception) {
                if (exception.getCause() instanceof SQLException cause && this.duplicateKey(cause)) return Status.DUPLICATE_NAME;
                throw exception;
            }
        }, this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Boolean> delete(@NotNull UUID id) {
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> handle.createUpdate("DELETE FROM " + this.table + " WHERE id = :id")
                .bind("id", this.encodeUuid(id))
                .execute() > 0), this.executor);
    }

    private <S extends SqlStatement<S>> S bindLocation(S statement, String server, WorldLocation location) {
        return statement.bind("server", server)
                .bind("world", location.world())
                .bind("x", location.x())
                .bind("y", location.y())
                .bind("z", location.z())
                .bind("yaw", location.yaw())
                .bind("pitch", location.pitch());
    }

    private Warp read(ResultSet row) throws SQLException {
        return new Warp(
                this.readUuid(row, "id"),
                row.getString("name"),
                row.getString("server"),
                new WorldLocation(row.getString("world"), row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"), row.getFloat("pitch")),
                this.readUuid(row, "creator"),
                row.getLong("created_at"),
                row.getLong("updated_at")
        );
    }

    protected abstract Object encodeUuid(UUID uuid);

    protected abstract int uuidSqlType();

    @Nullable
    protected abstract UUID readUuid(ResultSet row, String column) throws SQLException;

    protected abstract boolean duplicateKey(SQLException exception);
}
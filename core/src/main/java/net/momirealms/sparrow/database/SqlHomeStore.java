package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.util.WorldLocation;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class SqlHomeStore implements HomeStore {
    private static final String COLUMNS = "name_key = :name_key, name = :name, server = :server, world = :world, "
            + "x = :x, y = :y, z = :z, yaw = :yaw, pitch = :pitch, updated_at = :updated_at";

    private final Supplier<Jdbi> jdbi;
    private final Executor executor;
    private final String homes;
    private volatile boolean initialized;

    // 表名由调用方按方言加好引号.
    protected SqlHomeStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull String homes) {
        this.jdbi = jdbi;
        this.executor = executor;
        this.homes = homes;
    }

    protected abstract void migrate(@NotNull Jdbi jdbi);

    @NotNull
    protected abstract Object uuidValue(@NotNull UUID uuid);

    @NotNull
    protected abstract UUID readUuid(@NotNull ResultSet result, @NotNull String column) throws SQLException;

    protected abstract boolean duplicateName(@NotNull SQLException exception);

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::sql, this.executor);
    }

    private Jdbi sql() {
        Jdbi jdbi = this.jdbi.get();
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
    public CompletableFuture<List<Home>> loadByOwner(@NotNull UUID owner) {
        String sql = "SELECT * FROM " + this.homes + " WHERE owner = :owner ORDER BY name_key";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindUuid(handle.createQuery(sql), "owner", owner)
                .map((result, context) -> this.readHome(result)).list()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<Home>> find(@NotNull UUID id) {
        String sql = "SELECT * FROM " + this.homes + " WHERE id = :id";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindUuid(handle.createQuery(sql), "id", id)
                .map((result, context) -> this.readHome(result)).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<Home>> findByName(@NotNull UUID owner, @NotNull String nameIgnoreCase) {
        String sql = "SELECT * FROM " + this.homes + " WHERE owner = :owner AND name_key = :name_key";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindUuid(handle.createQuery(sql), "owner", owner).bind("name_key", Home.key(nameIgnoreCase))
                .map((result, context) -> this.readHome(result)).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Long> countByOwner(@NotNull UUID owner) {
        String sql = "SELECT COUNT(*) FROM " + this.homes + " WHERE owner = :owner";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindUuid(handle.createQuery(sql), "owner", owner).mapTo(Long.class).one()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<SaveResult> create(@NotNull Home home) {
        String insert = "INSERT INTO " + this.homes + " (id, owner, name_key, name, server, world, x, y, z, yaw, pitch, created_at, updated_at)"
                + " VALUES (:id, :owner, :name_key, :name, :server, :world, :x, :y, :z, :yaw, :pitch, :created_at, :updated_at)";
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.sql().useHandle(handle -> this.bindHome(handle.createUpdate(insert), home).bind("created_at", home.createdAt()).execute());
                return new SaveResult(Status.SUCCESS, home);
            } catch (UnableToExecuteStatementException exception) {
                if (!(exception.getCause() instanceof SQLException cause) || !this.duplicateName(cause)) {
                    throw exception;
                }
                return new SaveResult(Status.DUPLICATE_NAME, null);
            }
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<SaveResult> update(@NotNull Home home) {
        String update = "UPDATE " + this.homes + " SET " + COLUMNS + " WHERE owner = :owner AND id = :id";
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.sql().inTransaction(handle -> {
                    // 锁定同一记录, 返回本次写入保留的创建时间.
                    Home current = this.bindUuid(this.bindUuid(handle.createQuery("SELECT * FROM " + this.homes + " WHERE owner = :owner AND id = :id FOR UPDATE"), "owner", home.owner()), "id", home.id())
                            .map((result, context) -> this.readHome(result)).findOne().orElse(null);
                    if (current == null) return new SaveResult(Status.NOT_FOUND, null);
                    this.bindHome(handle.createUpdate(update), home).execute();
                    Home saved = new Home(current.id(), current.owner(), home.name(), home.server(), home.location(), current.createdAt(), home.updatedAt());
                    return new SaveResult(Status.SUCCESS, saved);
                });
            } catch (UnableToExecuteStatementException exception) {
                if (!(exception.getCause() instanceof SQLException cause) || !this.duplicateName(cause)) {
                    throw exception;
                }
                return new SaveResult(Status.DUPLICATE_NAME, null);
            }
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<DeleteResult>> delete(@NotNull UUID owner, @NotNull UUID id) {
        return CompletableFuture.supplyAsync(() -> this.sql().inTransaction(handle -> {
            // 删除与名称读取处于同一事务, 同步信息对应实际删除的记录.
            String nameKey = this.bindUuid(this.bindUuid(handle.createQuery("SELECT name_key FROM " + this.homes + " WHERE owner = :owner AND id = :id FOR UPDATE"), "owner", owner), "id", id)
                    .mapTo(String.class).findOne().orElse(null);
            if (nameKey == null) return Optional.empty();
            this.bindUuid(this.bindUuid(handle.createUpdate("DELETE FROM " + this.homes + " WHERE owner = :owner AND id = :id"), "owner", owner), "id", id).execute();
            return Optional.of(new DeleteResult(owner, id, nameKey));
        }), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Long> deleteByOwner(@NotNull UUID owner) {
        String sql = "DELETE FROM " + this.homes + " WHERE owner = :owner";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> (long) this.bindUuid(handle.createUpdate(sql), "owner", owner).execute()), this.executor);
    }

    private <S extends SqlStatement<S>> S bindHome(S statement, Home home) {
        WorldLocation location = home.location();
        this.bindUuid(statement, "id", home.id());
        this.bindUuid(statement, "owner", home.owner());
        return statement.bind("name_key", home.key())
                .bind("name", home.name())
                .bind("server", home.server())
                .bind("world", location.world())
                .bind("x", location.x())
                .bind("y", location.y())
                .bind("z", location.z())
                .bind("yaw", location.yaw())
                .bind("pitch", location.pitch())
                .bind("updated_at", home.updatedAt());
    }

    private <S extends SqlStatement<S>> S bindUuid(S statement, String name, @NotNull UUID uuid) {
        return statement.bind(name, this.uuidValue(uuid));
    }

    private Home readHome(ResultSet result) throws SQLException {
        WorldLocation location = new WorldLocation(result.getString("world"), result.getDouble("x"), result.getDouble("y"), result.getDouble("z"),
                result.getFloat("yaw"), result.getFloat("pitch"));
        return new Home(this.readUuid(result, "id"), this.readUuid(result, "owner"), result.getString("name"), result.getString("server"),
                location, result.getLong("created_at"), result.getLong("updated_at"));
    }
}

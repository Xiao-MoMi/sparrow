package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.util.WorldLocation;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jdbi.v3.core.statement.SqlStatement;
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

// MySQL 与 PostgreSQL 共用的 warp 读写, 子类负责建表和 UUID 列类型.
@ApiStatus.Internal
public abstract class SqlWarpStore implements WarpStore {
    private static final String COLUMNS = "name_key = :name_key, name = :name, description = :description, server = :server, world = :world, "
            + "x = :x, y = :y, z = :z, yaw = :yaw, pitch = :pitch, updated_at = :updated_at";

    private final Supplier<Jdbi> jdbi;
    private final Executor executor;
    private final String warps;
    private volatile boolean initialized; // 表结构已在本次运行中确认过

    // 表名需要调用方按方言加好引号
    protected SqlWarpStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull String warps) {
        this.jdbi = jdbi;
        this.executor = executor;
        this.warps = warps;
    }

    // 在迁移锁内创建或升级 warp 表
    protected abstract void migrate(@NotNull Jdbi jdbi);

    @NotNull
    protected abstract Object uuidValue(@NotNull UUID uuid);

    // 绑定空 UUID 时使用的 JDBC 类型
    protected abstract int uuidNullType();

    @Nullable
    protected abstract UUID readUuid(@NotNull ResultSet result, @NotNull String column) throws SQLException;

    protected abstract boolean duplicateKey(@NotNull SQLException exception);

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::sql, this.executor);
    }

    // 第一次使用时在数据库线程上准备表结构, 失败后下一次使用会重新尝试
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
    public CompletableFuture<List<Warp>> loadAll() {
        String sql = "SELECT * FROM " + this.warps + " ORDER BY name_key";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery(sql).map((result, context) -> this.readWarp(result)).list()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<Warp>> find(@NotNull UUID id) {
        String sql = "SELECT * FROM " + this.warps + " WHERE id = :id";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindUuid(handle.createQuery(sql), "id", id)
                .map((result, context) -> this.readWarp(result)).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<Warp>> findByName(@NotNull String nameIgnoreCase) {
        String sql = "SELECT * FROM " + this.warps + " WHERE name_key = :name_key";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createQuery(sql).bind("name_key", Warp.key(nameIgnoreCase))
                .map((result, context) -> this.readWarp(result)).findOne()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<SaveResult> create(@NotNull Warp warp) {
        String insert = "INSERT INTO " + this.warps + " (id, name_key, name, description, server, world, x, y, z, yaw, pitch, creator, created_at, updated_at)"
                + " VALUES (:id, :name_key, :name, :description, :server, :world, :x, :y, :z, :yaw, :pitch, :creator, :created_at, :updated_at)";
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.sql().useHandle(handle -> this.bindUuid(this.bindWarp(handle.createUpdate(insert), warp), "creator", warp.creator()).bind("created_at", warp.createdAt()).execute());
                return new SaveResult(Status.SUCCESS, warp);
            } catch (UnableToExecuteStatementException exception) {
                if (!(exception.getCause() instanceof SQLException cause) || !this.duplicateKey(cause)) {
                    throw exception;
                }
                return new SaveResult(Status.DUPLICATE_NAME, null);
            }
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<SaveResult> update(@NotNull Warp warp) {
        String update = "UPDATE " + this.warps + " SET " + COLUMNS + " WHERE id = :id";
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.sql().inTransaction(handle -> {
                    Warp current = this.bindUuid(handle.createQuery("SELECT * FROM " + this.warps + " WHERE id = :id FOR UPDATE"), "id", warp.id())
                            .map((result, context) -> this.readWarp(result)).findOne().orElse(null);
                    if (current == null) return new SaveResult(Status.NOT_FOUND, null);
                    this.bindWarp(handle.createUpdate(update), warp).execute();
                    Warp saved = new Warp(current.id(), warp.name(), warp.description(), warp.server(), warp.location(), current.creator(), current.createdAt(), warp.updatedAt());
                    return new SaveResult(Status.SUCCESS, saved);
                });
            } catch (UnableToExecuteStatementException exception) {
                if (!(exception.getCause() instanceof SQLException cause) || !this.duplicateKey(cause)) {
                    throw exception;
                }
                return new SaveResult(Status.DUPLICATE_NAME, null);
            }
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID id) {
        String sql = "DELETE FROM " + this.warps + " WHERE id = :id";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> this.bindUuid(handle.createUpdate(sql), "id", id).execute() > 0), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Integer> deleteByWorld(@NotNull String server, @NotNull String world) {
        String sql = "DELETE FROM " + this.warps + " WHERE server = :server AND world = :world";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createUpdate(sql).bind("server", server).bind("world", world).execute()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Integer> deleteByServer(@NotNull String server) {
        String sql = "DELETE FROM " + this.warps + " WHERE server = :server";
        return CompletableFuture.supplyAsync(() -> this.sql().withHandle(handle -> handle.createUpdate(sql).bind("server", server).execute()), this.executor);
    }

    private <S extends SqlStatement<S>> S bindWarp(S statement, Warp warp) {
        WorldLocation location = warp.location();
        this.bindUuid(statement, "id", warp.id());
        return statement.bind("name_key", warp.key())
                .bind("name", warp.name())
                .bind("description", warp.description())
                .bind("server", warp.server())
                .bind("world", location.world())
                .bind("x", location.x())
                .bind("y", location.y())
                .bind("z", location.z())
                .bind("yaw", location.yaw())
                .bind("pitch", location.pitch())
                .bind("updated_at", warp.updatedAt());
    }

    private <S extends SqlStatement<S>> S bindUuid(S statement, String name, @Nullable UUID uuid) {
        return uuid == null ? statement.bindNull(name, this.uuidNullType()) : statement.bind(name, this.uuidValue(uuid));
    }

    private Warp readWarp(ResultSet result) throws SQLException {
        WorldLocation location = new WorldLocation(result.getString("world"), result.getDouble("x"), result.getDouble("y"), result.getDouble("z"),
                result.getFloat("yaw"), result.getFloat("pitch"));
        return new Warp(
                this.readUuid(result, "id"),
                result.getString("name"),
                result.getString("description"),
                result.getString("server"),
                location,
                this.readUuid(result, "creator"),
                result.getLong("created_at"),
                result.getLong("updated_at")
        );
    }
}

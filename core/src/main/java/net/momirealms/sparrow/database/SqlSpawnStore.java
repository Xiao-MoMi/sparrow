package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.spawn.Spawn;
import net.momirealms.sparrow.util.WorldLocation;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class SqlSpawnStore implements SpawnStore {
    protected final String table;
    private final Supplier<Jdbi> jdbi;
    private final Executor executor;
    private volatile boolean initialized;

    protected SqlSpawnStore(@NotNull Supplier<Jdbi> jdbi, @NotNull Executor executor, @NotNull String table) {
        this.jdbi = jdbi;
        this.executor = executor;
        this.table = table;
    }

    protected abstract void migrate(@NotNull Jdbi jdbi);

    @NotNull
    protected abstract String saveSql();

    @NotNull
    private Jdbi getSql() {
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

    @NotNull
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::getSql, this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<Spawn>> load() {
        return CompletableFuture.supplyAsync(
                () -> this.getSql().withHandle(handle -> handle
                        .createQuery("SELECT * FROM " + this.table + " WHERE id = 1")
                        .map((result, context) -> this.readSpawn(result))
                        .findOne()),
                this.executor
        );
    }

    @NotNull
    private Spawn readSpawn(@NotNull ResultSet result) throws SQLException {
        return new Spawn(
                result.getString("server"),
                new WorldLocation(
                        result.getString("world"),
                        result.getDouble("x"),
                        result.getDouble("y"),
                        result.getDouble("z"),
                        result.getFloat("yaw"),
                        result.getFloat("pitch")
                )
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> save(@NotNull Spawn spawn) {
        return CompletableFuture.runAsync(
                () -> {
                    WorldLocation location = spawn.location();
                    this.getSql().useHandle(handle -> handle.createUpdate(this.saveSql())
                            .bind("server", spawn.server())
                            .bind("world", location.world())
                            .bind("x", location.x())
                            .bind("y", location.y())
                            .bind("z", location.z())
                            .bind("yaw", location.yaw())
                            .bind("pitch", location.pitch())
                            .execute());
                },
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Boolean> delete() {
        return CompletableFuture.supplyAsync(
                () -> this.getSql().withHandle(handle -> handle
                        .createUpdate("DELETE FROM " + this.table + " WHERE id = 1")
                        .execute() > 0),
                this.executor
        );
    }
}

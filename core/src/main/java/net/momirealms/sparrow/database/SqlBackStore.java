package net.momirealms.sparrow.database;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.NBT;
import net.momirealms.sparrow.util.ServerLocation;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class SqlBackStore implements BackStore {
    private final Jdbi sql;
    private final Executor executor;
    protected final String table;
    private final Map<OperationKey, CompletableFuture<?>> operations = new HashMap<>();

    protected SqlBackStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String table) {
        this.sql = sql;
        this.executor = executor;
        this.table = table;
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<ServerLocation>> loadBack(@NotNull UUID player) {
        return this.load(player, "back_location");
    }

    @NotNull
    @Override
    public CompletableFuture<Void> saveBack(@NotNull UUID player, @NotNull ServerLocation location) {
        return this.save(player, "back_location", location);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<ServerLocation>> loadDeathBack(@NotNull UUID player) {
        return this.load(player, "death_location");
    }

    @NotNull
    @Override
    public CompletableFuture<Void> saveDeathBack(@NotNull UUID player, @NotNull ServerLocation location) {
        return this.save(player, "death_location", location);
    }

    private CompletableFuture<Optional<ServerLocation>> load(UUID player, String column) {
        return this.execute(player, column, () -> this.sql.withHandle(handle -> handle.createQuery("SELECT " + column + " FROM " + this.table + " WHERE uuid = :uuid")
                .bind("uuid", this.encodeUuid(player))
                .map((result, context) -> {
                    byte[] bytes = result.getBytes(column);
                    if (bytes == null) return Optional.<ServerLocation>empty();
                    try {
                        CompoundTag tag = NBT.fromBytes(bytes);
                        if (tag == null) throw new IllegalArgumentException("Back location must contain a compound");
                        return Optional.of(ServerLocation.fromNbt(tag));
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                })
                .findOne()
                .orElseGet(Optional::empty)));
    }

    private CompletableFuture<Void> save(UUID player, String column, ServerLocation location) {
        return this.execute(player, column, () -> {
            byte[] bytes;
            try {
                bytes = NBT.toBytes(location.toNbt());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            this.sql.useHandle(handle -> handle.createUpdate(this.upsert(column))
                    .bind("uuid", this.encodeUuid(player))
                    .bind("location", bytes)
                    .execute());
            return null;
        });
    }

    private <T> CompletableFuture<T> execute(UUID player, String column, Supplier<T> operation) {
        OperationKey key = new OperationKey(player, column);
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<?> previous;
        synchronized (this.operations) {
            previous = this.operations.put(key, result);
        }
        // 前序操作的结果留给原调用方, 此处只等待 SQL 执行结束再启动下一次读写
        CompletableFuture<?> ready = previous == null ? CompletableFuture.completedFuture(null) : previous.handle((value, error) -> null);
        ready.thenApplyAsync(ignored -> operation.get(), this.executor).whenComplete((value, error) -> {
            synchronized (this.operations) {
                this.operations.remove(key, result);
            }
            if (error == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(error);
            }
        });
        return result.copy();
    }

    protected abstract Object encodeUuid(UUID player);

    protected abstract String upsert(String column);

    private record OperationKey(UUID player, String column) {
    }
}
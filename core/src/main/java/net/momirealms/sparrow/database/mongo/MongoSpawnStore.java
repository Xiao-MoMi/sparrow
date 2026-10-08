package net.momirealms.sparrow.database.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import net.momirealms.sparrow.database.SpawnStore;
import net.momirealms.sparrow.feature.spawn.Spawn;
import net.momirealms.sparrow.util.WorldLocation;
import org.bson.Document;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
final class MongoSpawnStore implements SpawnStore {
    private final Supplier<MongoDatabase> database;
    private final Executor executor;
    private final String collection;

    MongoSpawnStore(@NotNull Supplier<MongoDatabase> database, @NotNull Executor executor, @NotNull String prefix) {
        this.database = database;
        this.executor = executor;
        this.collection = prefix + "spawn";
    }

    @NotNull
    private MongoCollection<Document> getCollection() {
        return this.database.get().getCollection(this.collection);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::getCollection, this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<Spawn>> load() {
        return CompletableFuture.supplyAsync(
                () -> {
                    Document document = this.getCollection().find(Filters.eq("_id", "spawn")).first();
                    if (document == null) {
                        return Optional.empty();
                    }
                    return Optional.of(new Spawn(
                            document.getString("server"),
                            new WorldLocation(
                                    document.getString("world"),
                                    document.getDouble("x"),
                                    document.getDouble("y"),
                                    document.getDouble("z"),
                                    document.getDouble("yaw").floatValue(),
                                    document.getDouble("pitch").floatValue()
                            )
                    ));
                },
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Void> save(@NotNull Spawn spawn) {
        return CompletableFuture.runAsync(
                () -> {
                    WorldLocation location = spawn.location();
                    Document document = new Document("_id", "spawn")
                            .append("server", spawn.server())
                            .append("world", location.world())
                            .append("x", location.x())
                            .append("y", location.y())
                            .append("z", location.z())
                            .append("yaw", (double) location.yaw())
                            .append("pitch", (double) location.pitch());
                    this.getCollection().replaceOne(Filters.eq("_id", "spawn"), document, new ReplaceOptions().upsert(true));
                },
                this.executor
        );
    }

    @NotNull
    @Override
    public CompletableFuture<Boolean> delete() {
        return CompletableFuture.supplyAsync(() -> this.getCollection().deleteOne(Filters.eq("_id", "spawn")).getDeletedCount() > 0, this.executor);
    }
}
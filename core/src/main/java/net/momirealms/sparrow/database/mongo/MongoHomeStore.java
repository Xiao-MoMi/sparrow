package net.momirealms.sparrow.database.mongo;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.WorldLocation;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
final class MongoHomeStore implements HomeStore {
    private static final String SCHEMA_ID = "home_schema";
    private static final Map<String, List<IndexReconciler.IndexDeclaration>> INDEXES = Map.of(
            "homes", List.of(
                    new IndexReconciler.IndexDeclaration(new Document("owner", 1).append("name_key", 1), true, "homes_owner_name"),
                    new IndexReconciler.IndexDeclaration(new Document("server", 1).append("world", 1), false, "homes_location"),
                    new IndexReconciler.IndexDeclaration(new Document("world", 1), false, "homes_world"))
    );

    private final Supplier<MongoDatabase> database;
    private final Executor executor;
    private final PluginLogger logger;
    private final String prefix;
    private volatile MongoCollection<Document> homes;

    MongoHomeStore(@NotNull Supplier<MongoDatabase> database, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        this.database = database;
        this.executor = executor;
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::homes, this.executor);
    }

    private MongoCollection<Document> homes() {
        MongoCollection<Document> prepared = this.homes;
        if (prepared != null) return prepared;
        synchronized (this) {
            if (this.homes == null) {
                MongoDatabase database = this.database.get();
                IndexReconciler.reconcile(this.logger, database, this.prefix, SCHEMA_ID, DependencyVersions.MONGODB_HOME_INDEX_VERSION, INDEXES);
                this.homes = database.getCollection(this.prefix + "homes");
            }
            return this.homes;
        }
    }

    @Override
    @NotNull
    public CompletableFuture<List<Home>> loadByOwner(@NotNull UUID owner) {
        return CompletableFuture.supplyAsync(() -> this.homes().find(Filters.eq("owner", owner)).sort(Sorts.ascending("name_key")).map(MongoHomeStore::readHome).into(new ArrayList<>()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<Home>> find(@NotNull UUID id) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(this.homes().find(Filters.eq("_id", id)).map(MongoHomeStore::readHome).first()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<Home>> findByName(@NotNull UUID owner, @NotNull String nameIgnoreCase) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(this.homes().find(Filters.and(Filters.eq("owner", owner), Filters.eq("name_key", Home.key(nameIgnoreCase))))
                .map(MongoHomeStore::readHome).first()), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Long> countByOwner(@NotNull UUID owner) {
        return CompletableFuture.supplyAsync(() -> this.homes().countDocuments(Filters.eq("owner", owner)), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<SaveResult> create(@NotNull Home home) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = mutableFields(home).append("_id", home.id()).append("owner", home.owner()).append("created_at", home.createdAt());
            MongoCollection<Document> homes = this.homes();
            try {
                homes.insertOne(document);
                return new SaveResult(Status.SUCCESS, home);
            } catch (MongoException exception) {
                if (exception.getCode() != 11000) {
                    throw exception;
                }
                return new SaveResult(Status.DUPLICATE_NAME, null);
            }
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<SaveResult> update(@NotNull Home home) {
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> homes = this.homes();
            try {
                Document saved = homes.findOneAndUpdate(Filters.and(Filters.eq("owner", home.owner()), Filters.eq("_id", home.id())), new Document("$set", mutableFields(home)),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
                return saved == null ? new SaveResult(Status.NOT_FOUND, null) : new SaveResult(Status.SUCCESS, readHome(saved));
            } catch (MongoException exception) {
                if (exception.getCode() != 11000) {
                    throw exception;
                }
                return new SaveResult(Status.DUPLICATE_NAME, null);
            }
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID owner, @NotNull UUID id) {
        return CompletableFuture.supplyAsync(() -> this.homes().deleteOne(Filters.and(Filters.eq("owner", owner), Filters.eq("_id", id))).getDeletedCount() > 0, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Long> deleteAll(@NotNull Filter filter) {
        List<Bson> conditions = new ArrayList<>();
        if (filter.owner() != null) {
            conditions.add(Filters.eq("owner", filter.owner()));
        }
        if (filter.server() != null) {
            conditions.add(Filters.eq("server", filter.server()));
        }
        if (filter.world() != null) {
            conditions.add(Filters.eq("world", filter.world()));
        }
        return CompletableFuture.supplyAsync(() -> this.homes().deleteMany(Filters.and(conditions)).getDeletedCount(), this.executor);
    }

    private static Document mutableFields(Home home) {
        WorldLocation location = home.location();
        return new Document("name_key", home.key())
                .append("name", home.name())
                .append("server", home.server())
                .append("world", location.world())
                .append("x", location.x())
                .append("y", location.y())
                .append("z", location.z())
                .append("yaw", (double) location.yaw())
                .append("pitch", (double) location.pitch())
                .append("updated_at", home.updatedAt());
    }

    private static Home readHome(Document document) {
        WorldLocation location = new WorldLocation(document.getString("world"), document.getDouble("x"), document.getDouble("y"), document.getDouble("z"),
                document.getDouble("yaw").floatValue(), document.getDouble("pitch").floatValue());
        return new Home(document.get("_id", UUID.class), document.get("owner", UUID.class), document.getString("name"), document.getString("server"),
                location, document.getLong("created_at"), document.getLong("updated_at"));
    }
}

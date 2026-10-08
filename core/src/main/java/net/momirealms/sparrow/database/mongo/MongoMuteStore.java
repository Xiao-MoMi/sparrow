package net.momirealms.sparrow.database.mongo;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import net.momirealms.sparrow.database.MuteStore;
import net.momirealms.sparrow.feature.mute.MuteRecord;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

final class MongoMuteStore implements MuteStore {
    private static final Map<String, List<IndexReconciler.IndexDeclaration>> INDEXES = Map.of(
            "mutes",
            List.of(
                    new IndexReconciler.IndexDeclaration(new Document("active_key", 1), true, "mutes_active"),
                    new IndexReconciler.IndexDeclaration(new Document("player", 1).append("created_at", -1), false, "mutes_player")
            )
    );

    private final Supplier<MongoDatabase> database;
    private final Executor executor;
    private final PluginLogger logger;
    private final String prefix;
    private volatile MongoCollection<Document> collection;

    MongoMuteStore(@NotNull Supplier<MongoDatabase> database, @NotNull Executor executor, @NotNull PluginLogger logger, @NotNull String prefix) {
        this.database = database;
        this.executor = executor;
        this.logger = logger;
        this.prefix = prefix;
    }

    @NotNull
    private MongoCollection<Document> getCollection() {
        MongoCollection<Document> prepared = this.collection;
        if (prepared != null) return prepared;
        synchronized (this) {
            if (this.collection == null) {
                MongoDatabase database = this.database.get();
                IndexReconciler.reconcile(this.logger, database, this.prefix, "mute_schema", DependencyVersions.MONGODB_MUTE_INDEX_VERSION, INDEXES);
                this.collection = database.getCollection(this.prefix + "mutes");
            }
            return this.collection;
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(this::getCollection, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<MuteRecord>> findActive(@NotNull UUID player, long now) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.getCollection().find(Filters.and(Filters.eq("active_key", "player:" + player), Filters.gt("expires_at", now))).first();
            return Optional.ofNullable(document).map(MongoMuteStore::read);
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> create(@NotNull MuteRecord record) {
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> collection = this.getCollection();
            String key = "player:" + record.player();
            Document previous = collection.find(Filters.eq("active_key", key)).first();
            if (previous != null) {
                if (previous.getLong("expires_at") > record.createdAt()) return false;
                // 只释放本次读到的过期记录, 并发写入的新禁言仍由唯一索引保护.
                collection.updateOne(
                        Filters.and(Filters.eq("_id", previous.getString("_id")), Filters.eq("active_key", key)),
                        Updates.set("active_key", "history:" + previous.getString("_id"))
                );
            }
            Document document = new Document("_id", record.id())
                    .append("active_key", key)
                    .append("player", record.player())
                    .append("player_name", record.playerName())
                    .append("reason", record.reason())
                    .append("operator_name", record.operatorName())
                    .append("server", record.server())
                    .append("created_at", record.createdAt())
                    .append("expires_at", record.expiresAt())
                    .append("revoked_at", 0L);
            try {
                collection.insertOne(document);
                return true;
            } catch (MongoWriteException exception) {
                if (exception.getError().getCode() == 11000) return false;
                throw exception;
            }
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<MuteRecord>> revoke(@NotNull UUID player, long now, @NotNull String operator) {
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> collection = this.getCollection();
            String key = "player:" + player;
            Document current = collection.find(Filters.and(Filters.eq("active_key", key), Filters.gt("expires_at", now))).first();
            if (current == null) return Optional.empty();
            Document revoked = collection.findOneAndUpdate(
                    Filters.and(Filters.eq("_id", current.getString("_id")), Filters.eq("active_key", key)),
                    Updates.combine(
                            Updates.set("active_key", "history:" + current.getString("_id")),
                            Updates.set("revoked_at", now),
                            Updates.set("revoked_by", operator)
                    ),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            );
            return Optional.ofNullable(revoked).map(MongoMuteStore::read);
        }, this.executor);
    }

    @NotNull
    private static MuteRecord read(Document document) {
        return new MuteRecord(
                document.getString("_id"),
                document.get("player", UUID.class),
                document.getString("player_name"),
                document.getString("reason"),
                document.getString("operator_name"),
                document.getString("server"),
                document.getLong("created_at"),
                document.getLong("expires_at"),
                document.getLong("revoked_at"),
                document.getString("revoked_by")
        );
    }
}
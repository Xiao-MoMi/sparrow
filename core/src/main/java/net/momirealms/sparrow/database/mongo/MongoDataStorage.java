package net.momirealms.sparrow.database.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

// 用户文档以玩家 UUID 作为 _id, 连接使用 STANDARD UUID 编码.
@ApiStatus.Internal
public final class MongoDataStorage extends DataStorage {
    private static final String USER_NAME = "name";
    private static final String USER_UPDATED_AT = "updated_at";

    private MongoClient client;
    private MongoCollection<Document> data;

    public MongoDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
    }

    @Override
    public void initialize() {
        PluginConfig.MongoOptions mongoOptions = this.options.mongodb();
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(10, TimeUnit.SECONDS))
                .applyConnectionString(new ConnectionString(mongoOptions.url()));
        if (!mongoOptions.username().isEmpty()) {
            builder.credential(MongoCredential.createCredential(mongoOptions.username(), mongoOptions.authSource(), mongoOptions.password().toCharArray()));
        }
        MongoClient connected = MongoClients.create(builder.build());
        try {
            MongoDatabase database = connected.getDatabase(mongoOptions.database());
            database.runCommand(new Document("ping", 1));
            MongoCollection<Document> data = database.getCollection(this.namePrefix() + "data");
            IndexReconciler.reconcile(this.logger, database, this.namePrefix());
            this.client = connected;
            this.data = data;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogin(@NotNull UUID player, @NotNull String name, long timestamp) {
        return this.save(player, name, timestamp, null, null);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogout(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.save(player, name, timestamp, server, location);
    }

    private CompletableFuture<Void> save(UUID player, String name, long timestamp, String server, WorldLocation location) {
        boolean logout = location != null;
        String time = logout ? "last_logout" : "last_login";
        Document newer = new Document("$gte", List.of(timestamp, new Document("$ifNull", List.of("$" + time, 0L))));
        Document current = new Document("$ifNull", List.of("$" + USER_UPDATED_AT, 0L));
        Document fields = new Document(USER_NAME, new Document("$cond", List.of(new Document("$gte", List.of(timestamp, current)), new Document("$literal", name), "$name")))
                .append(USER_UPDATED_AT, new Document("$max", List.of(timestamp, current)))
                .append(time, new Document("$max", List.of(timestamp, new Document("$ifNull", List.of("$" + time, 0L)))))
                .append(logout ? "last_login" : "last_logout", new Document("$ifNull", List.of(logout ? "$last_login" : "$last_logout", 0L)));
        if (logout) {
            Document values = new Document("last_server", server).append("last_world", location.world()).append("x", location.x()).append("y", location.y())
                    .append("z", location.z()).append("yaw", (double) location.yaw()).append("pitch", (double) location.pitch());
            values.forEach((key, value) -> fields.append(key, new Document("$cond", List.of(newer, new Document("$literal", value), "$" + key))));
        }
        return CompletableFuture.runAsync(() -> this.data().updateOne(Filters.eq("_id", player), List.of(new Document("$set", fields)), new UpdateOptions().upsert(true)), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> loadPlayer(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.data().find(Filters.eq("_id", player)).first();
            if (document == null) return Optional.empty();
            String world = document.getString("last_world");
            WorldLocation location = world == null ? null : new WorldLocation(world, document.getDouble("x"), document.getDouble("y"), document.getDouble("z"), document.getDouble("yaw").floatValue(), document.getDouble("pitch").floatValue());
            return Optional.of(new PlayerData(player, document.getString(USER_NAME), document.getLong("last_login"), document.getLong("last_logout"), document.getString("last_server"), location, document.getLong(USER_UPDATED_AT)));
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.data().find(Filters.eq(USER_NAME, name)).sort(Sorts.descending(USER_UPDATED_AT, "_id")).projection(Projections.include("_id")).limit(1).first();
            return document == null ? Optional.empty() : Optional.of(document.get("_id", UUID.class));
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.data().find(Filters.eq("_id", player)).projection(Projections.include(USER_NAME)).first();
            return document == null ? Optional.empty() : Optional.of(document.getString(USER_NAME));
        }, this.executor);
    }

    private MongoCollection<Document> data() {
        if (this.data == null) throw new IllegalStateException("MongoDB is not initialized");
        return this.data;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.close();
    }
}

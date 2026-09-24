package net.momirealms.sparrow.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

// 用户文档以玩家 UUID 作为 _id, 连接使用 STANDARD UUID 编码.
final class MongoDataStorage extends DataStorage {
    private static final String USER_NAME = "name";
    private static final String USER_LAST_SEEN = "last_seen";

    private MongoClient client;
    private MongoCollection<Document> users;

    MongoDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor) {
        super(options, executor);
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
            MongoCollection<Document> users = database.getCollection(this.namePrefix() + "users");
            // 同名记录可以并存, 查询按最后出现时间取最近一条.
            users.createIndex(Indexes.compoundIndex(Indexes.ascending(USER_NAME), Indexes.descending(USER_LAST_SEEN)), new IndexOptions().name("user_name_seen"));
            this.client = connected;
            this.users = users;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveUser(@NotNull UUID player, @NotNull String name) {
        return CompletableFuture.runAsync(() -> this.users().replaceOne(Filters.eq("_id", player),
                new Document("_id", player).append(USER_NAME, name).append(USER_LAST_SEEN, System.currentTimeMillis()),
                new ReplaceOptions().upsert(true)), this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.users().find(Filters.eq(USER_NAME, name)).sort(Sorts.descending(USER_LAST_SEEN)).projection(Projections.include("_id")).limit(1).first();
            return document == null ? Optional.empty() : Optional.of(document.get("_id", UUID.class));
        }, this.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.users().find(Filters.eq("_id", player)).projection(Projections.include(USER_NAME)).first();
            return document == null ? Optional.empty() : Optional.of(document.getString(USER_NAME));
        }, this.executor);
    }

    private MongoCollection<Document> users() {
        if (this.users == null) throw new IllegalStateException("MongoDB is not initialized");
        return this.users;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.close();
    }
}

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
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.database.SpawnStore;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.IpRange;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

// 用户文档以玩家 UUID 作为 _id, 连接使用 STANDARD UUID 编码.
@ApiStatus.Internal
public final class MongoDataStorage extends DataStorage {
    static final String SCHEMA_ID = "schema";
    static final Map<String, List<IndexReconciler.IndexDeclaration>> INDEXES = Map.of(
            "data", List.of(
                    new IndexReconciler.IndexDeclaration(new Document("name", 1).append("updated_at", -1).append("_id", -1), false, "data_name_updated"),
                    new IndexReconciler.IndexDeclaration(new Document("last_login_ip", 1), false, "data_login_ip"))
    );

    private static final String USER_NAME = "name";
    private static final String USER_UPDATED_AT = "updated_at";
    private static final String USER_LOGIN_IP = "last_login_ip";

    private final MongoBanStore banStore;
    private final MongoWarpStore warpStore;
    private final MongoHomeStore homeStore;
    private final MongoSpawnStore spawnStore;
    private MongoClient client;
    private MongoDatabase database;
    private MongoCollection<Document> data;

    public MongoDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
        this.banStore = new MongoBanStore(this::database, executor, logger, this.namePrefix());
        this.warpStore = new MongoWarpStore(this::database, executor, logger, this.namePrefix());
        this.homeStore = new MongoHomeStore(this::database, executor, logger, this.namePrefix());
        this.spawnStore = new MongoSpawnStore(this::database, executor, this.namePrefix());
    }

    @Override
    public void initialize() {
        PluginConfig.MongoOptions mongoOptions = super.options.mongodb();
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
            IndexReconciler.reconcile(super.logger, database, this.namePrefix(), SCHEMA_ID, DependencyVersions.MONGODB_DATA_INDEX_VERSION, INDEXES);
            this.client = connected;
            this.database = database;
            this.data = data;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogin(@NotNull UUID player, @NotNull String name, long ip, long timestamp) {
        return this.save(player, name, timestamp, ip, null, null, "last_login");
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveLogout(
            @NotNull UUID player,
            @NotNull String name,
            long timestamp,
            @NotNull String server,
            @NotNull WorldLocation location
    ) {
        return this.save(player, name, timestamp, IpRange.NONE, server, location, "last_logout");
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveDeath(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.save(player, name, timestamp, IpRange.NONE, server, location, "last_death");
    }

    @NotNull
    private CompletableFuture<Void> save(
            @NotNull UUID player,
            @NotNull String name,
            long timestamp,
            long ip,
            @Nullable String server,
            @Nullable WorldLocation location,
            @NotNull String time
    ) {
        boolean withLocation = location != null;
        Document newer = new Document("$gte", List.of(timestamp, new Document("$ifNull", List.of("$" + time, 0L))));
        Document current = new Document("$ifNull", List.of("$" + USER_UPDATED_AT, 0L));
        Document fields = new Document(
                USER_NAME,
                new Document("$cond", List.of(new Document("$gte", List.of(timestamp, current)), new Document("$literal", name), "$name"))
        ).append(USER_UPDATED_AT, new Document("$max", List.of(timestamp, current)));
        String[] times = {"last_login", "last_logout", "last_death"};
        for (String field : times) {
            fields.append(field, new Document("$ifNull", List.of("$" + field, 0L)));
        }
        fields.append(time, new Document("$max", List.of(timestamp, new Document("$ifNull", List.of("$" + time, 0L)))));
        if (withLocation) {
            Document values = new Document(time + "_server", server).append(time + "_location", Document.parse(location.toJson()));
            values.forEach((key, value) -> fields.append(key, new Document("$cond", List.of(newer, new Document("$literal", value), "$" + key))));
        }
        if (ip != IpRange.NONE) {
            fields.append(USER_LOGIN_IP, new Document("$cond", List.of(newer, ip, "$" + USER_LOGIN_IP)));
        }
        return CompletableFuture.runAsync(
                () -> this.data().updateOne(Filters.eq("_id", player), List.of(new Document("$set", fields)), new UpdateOptions().upsert(true)),
                super.executor
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> loadPlayer(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.data().find(Filters.eq("_id", player)).first();
            return document == null ? Optional.empty() : Optional.of(readPlayer(document));
        }, super.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Long> countPlayersOnIp(@NotNull IpRange range) {
        Bson filter = ipFilter(range);
        return CompletableFuture.supplyAsync(() -> this.data().countDocuments(filter), super.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<PlayerData>> listPlayersOnIp(@NotNull IpRange range, int offset, int limit) {
        Bson filter = ipFilter(range);
        return CompletableFuture.supplyAsync(() -> this.data()
                .find(filter)
                .sort(Sorts.descending("last_login", "_id"))
                .skip(offset)
                .limit(limit)
                .map(MongoDataStorage::readPlayer)
                .into(new ArrayList<>()), super.executor);
    }

    // IPv4 按 Long 比较
    private static Bson ipFilter(IpRange range) {
        return Filters.and(Filters.gte(USER_LOGIN_IP, range.start()), Filters.lte(USER_LOGIN_IP, range.end()));
    }

    private static PlayerData readPlayer(Document document) {
        Document stored = document.get("last_logout_location", Document.class);
        WorldLocation location = stored == null ? null : WorldLocation.fromJson(stored.toJson());
        Document deathStored = document.get("last_death_location", Document.class);
        WorldLocation deathLocation = deathStored == null ? null : WorldLocation.fromJson(deathStored.toJson());
        Long ip = document.getLong(USER_LOGIN_IP);
        return new PlayerData(
                document.get("_id", UUID.class),
                document.getString(USER_NAME),
                document.getLong("last_login"),
                document.getLong("last_logout"),
                document.getString("last_logout_server"),
                location,
                document.getLong("last_death"),
                document.getString("last_death_server"),
                deathLocation,
                ip == null ? null : IpRange.format(ip),
                document.getLong(USER_UPDATED_AT)
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.data()
                    .find(Filters.eq(USER_NAME, name))
                    .sort(Sorts.descending(USER_UPDATED_AT, "_id"))
                    .projection(Projections.include("_id"))
                    .limit(1)
                    .first();
            return document == null ? Optional.empty() : Optional.of(document.get("_id", UUID.class));
        }, super.executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.data().find(Filters.eq("_id", player)).projection(Projections.include(USER_NAME)).first();
            return document == null ? Optional.empty() : Optional.of(document.getString(USER_NAME));
        }, super.executor);
    }

    @Override
    @NotNull
    public BanStore banStore() {
        return this.banStore;
    }

    @Override
    @NotNull
    public WarpStore warpStore() {
        return this.warpStore;
    }

    @Override
    @NotNull
    public HomeStore homeStore() {
        return this.homeStore;
    }

    @NotNull
    @Override
    public SpawnStore spawnStore() {
        return this.spawnStore;
    }

    private MongoDatabase database() {
        if (this.database == null) throw new IllegalStateException("MongoDB is not initialized");
        return this.database;
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
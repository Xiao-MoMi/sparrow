package net.momirealms.sparrow.database.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatStartedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.player.PlayerProfile;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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
    // 预加载心跳事件类, 供插件类加载器关闭后仍在结束心跳的驱动线程使用.
    @SuppressWarnings("unused")
    private static final Class<?>[] HEARTBEAT_EVENT_CLASSES = {
            ServerHeartbeatStartedEvent.class,
            ServerHeartbeatSucceededEvent.class,
            ServerHeartbeatFailedEvent.class
    };

    static final String SCHEMA_ID = "schema";
    static final Map<String, List<IndexReconciler.IndexDeclaration>> INDEXES = Map.of("data", List.of());

    private MongoClient client;
    private MongoPlayerProfileStore profileStore;

    public MongoDataStorage(@NotNull PluginConfig.DatabaseOptions options, @NotNull Executor executor, @NotNull PluginLogger logger) {
        super(options, executor, logger);
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
            this.profileStore = new MongoPlayerProfileStore(data, super.executor);
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    @NotNull
    @Override
    public DataStore<UUID, String, PlayerProfile> playerProfileStore() {
        return this.profileStore;
    }

    @NotNull
    @Override
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        return this.profileStore.lookupUser(name);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.close();
    }
}
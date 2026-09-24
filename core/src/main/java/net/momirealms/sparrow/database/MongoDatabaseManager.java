package net.momirealms.sparrow.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class MongoDatabaseManager extends DatabaseManager {
    private MongoClient client;
    private MongoDatabase database;

    MongoDatabaseManager(@NotNull PluginConfig.DatabaseOptions options) {
        super(options);
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
            this.client = connected;
            this.database = database;
        } catch (RuntimeException exception) {
            connected.close();
            throw exception;
        }
    }

    /**
     * 返回 MongoDB 数据库句柄。同步驱动的读写会阻塞当前线程。
     *
     * @return 已连接的 MongoDB 数据库
     * @throws IllegalStateException 连接尚未建立时
     */
    @NotNull
    public MongoDatabase mongo() {
        if (this.database == null) throw new IllegalStateException("MongoDB is not initialized");
        return this.database;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.close();
    }
}

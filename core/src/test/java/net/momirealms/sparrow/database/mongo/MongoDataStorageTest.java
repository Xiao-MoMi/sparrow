package net.momirealms.sparrow.database.mongo;

import com.mongodb.client.MongoClients;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.bson.Document;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class MongoDataStorageTest {

    @Test
    void mongoStoresNamesAndPrefersLatestOwner() throws Exception {
        PluginConfig.DatabaseOptions options = new PluginConfig.DatabaseOptions();
        String prefix = "test_" + UUID.randomUUID().toString().replace("-", "") + "_";
        Field field = PluginConfig.MongoOptions.class.getDeclaredField("collectionPrefix");
        field.setAccessible(true);
        field.set(options.mongodb(), prefix);
        DataStorage storage = DataStorage.create(options, Runnable::run, mock(PluginLogger.class));
        try {
            storage.initialize();
        } catch (RuntimeException exception) {
            Assumptions.abort("local MongoDB is not reachable: " + exception.getMessage());
        }
        try {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            // 未知名字查不到记录
            assertEquals(Optional.empty(), storage.lookupUser("Steve").join());
            // 同名时取最近写入的玩家, 名字区分大小写
            storage.saveLogin(first, "Steve", System.currentTimeMillis()).join();
            Thread.sleep(5);
            storage.saveLogin(second, "Steve", System.currentTimeMillis()).join();
            assertEquals(Optional.of(second), storage.lookupUser("Steve").join());
            assertEquals(Optional.empty(), storage.lookupUser("steve").join());
            // 改名覆盖旧名字
            storage.saveLogin(second, "Alex", System.currentTimeMillis()).join();
            assertEquals(Optional.of("Alex"), storage.lookupName(second).join());
            assertEquals(Optional.of(first), storage.lookupUser("Steve").join());
            WorldLocation location = new WorldLocation("$world", -10.25, 72.5, 14.75, 123.5f, -12.75f);
            long now = System.currentTimeMillis() + 100;
            storage.saveLogout(second, "Alex", now, "$survival", location).join();
            storage.saveLogin(second, "AlexNew", now + 2).join();
            storage.saveLogout(second, "AlexOld", now - 1, "old", new WorldLocation("old", 1, 2, 3, 0, 0)).join();
            storage.saveLogin(second, "AlexOld", now - 2).join();
            PlayerData saved = storage.loadPlayer(second).join().orElseThrow();
            assertEquals("AlexNew", saved.name());
            assertEquals(now + 2, saved.lastLogin());
            assertEquals(now, saved.lastLogout());
            assertEquals(now + 2, saved.updatedAt());
            assertEquals("$survival", saved.lastServer());
            assertEquals(location, saved.lastLocation());
            try (var client = MongoClients.create(options.mongodb().url()); var translations = mockStatic(TranslationManager.class)) {
                var database = client.getDatabase(options.mongodb().database());
                var collection = database.getCollection(prefix + "data");
                collection.createIndex(new Document("stale", 1));
                IndexReconciler.reconcile(mock(PluginLogger.class), database, prefix);
                var indexes = collection.listIndexes().into(new ArrayList<>());
                assertEquals(2, indexes.size());
                var meta = database.getCollection(prefix + "meta");
                assertEquals(1, meta.find(new Document("_id", "schema")).first().getInteger("version"));
                meta.updateOne(new Document("_id", "schema"), new Document("$set", new Document("version", 2)));
                assertThrows(IllegalStateException.class, () -> IndexReconciler.reconcile(mock(PluginLogger.class), database, prefix));
                assertEquals(2, meta.find(new Document("_id", "schema")).first().getInteger("version"));
            }
        } finally {
            storage.close();
            try (var client = MongoClients.create(options.mongodb().url())) {
                client.getDatabase(options.mongodb().database()).getCollection(prefix + "data").drop();
                client.getDatabase(options.mongodb().database()).getCollection(prefix + "meta").drop();
            }
        }
    }
}

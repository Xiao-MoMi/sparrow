package net.momirealms.sparrow.database;

import com.mongodb.client.MongoClients;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataStorageTest {

    @Test
    void mongoStoresNamesAndPrefersLatestOwner() throws Exception {
        PluginConfig.DatabaseOptions options = new PluginConfig.DatabaseOptions();
        String prefix = "test_" + UUID.randomUUID().toString().replace("-", "") + "_";
        Field field = PluginConfig.MongoOptions.class.getDeclaredField("collectionPrefix");
        field.setAccessible(true);
        field.set(options.mongodb(), prefix);
        DataStorage storage = DataStorage.create(options, Runnable::run);
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
            storage.saveUser(first, "Steve").join();
            Thread.sleep(5);
            storage.saveUser(second, "Steve").join();
            assertEquals(Optional.of(second), storage.lookupUser("Steve").join());
            assertEquals(Optional.empty(), storage.lookupUser("steve").join());
            // 改名覆盖旧名字
            storage.saveUser(second, "Alex").join();
            assertEquals(Optional.of("Alex"), storage.lookupName(second).join());
            assertEquals(Optional.of(first), storage.lookupUser("Steve").join());
        } finally {
            storage.close();
            try (var client = MongoClients.create(options.mongodb().url())) {
                client.getDatabase(options.mongodb().database()).getCollection(prefix + "users").drop();
            }
        }
    }
}

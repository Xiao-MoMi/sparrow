package net.momirealms.sparrow.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.zaxxer.hikari.HikariDataSource;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpMessage;
import net.momirealms.sparrow.feature.warp.WarpRegistry;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

// 1 万个 warp 的加载、查找、补全与同步消息耗时. 设置 SPARROW_BENCHMARK=true 时才运行
class WarpLoadBenchmark {
    private static final int COUNT = 10_000;
    private static final int LIMIT = 100;

    @Test
    void measure() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("SPARROW_BENCHMARK")), "SPARROW_BENCHMARK is not set");
        List<Warp> warps = generate();
        this.sql(DatabaseType.MYSQL, warps);
        this.sql(DatabaseType.MARIADB, warps);
        this.sql(DatabaseType.POSTGRESQL, warps);
        this.mongo(warps);
        this.memory(warps);
    }

    private void sql(DatabaseType type, List<Warp> warps) throws Exception {
        String env = "SPARROW_TEST_" + type.name();
        String url = System.getenv(env + "_URL");
        String user = System.getenv(env + "_USERNAME");
        String password = System.getenv(env + "_PASSWORD");
        if (url == null) {
            System.out.println(type + ": skipped, " + env + "_URL is not set");
            return;
        }
        PluginConfig.DatabaseOptions options = new PluginConfig.DatabaseOptions();
        set(options, "type", type);
        PluginConfig.SqlOptions selected = switch (type) {
            case POSTGRESQL -> options.postgresql();
            case MARIADB -> options.mariadb();
            default -> options.mysql();
        };
        String prefix = "bench_" + UUID.randomUUID().toString().replace("-", "") + "_";
        set(selected, "tablePrefix", prefix);
        boolean postgres = type == DatabaseType.POSTGRESQL;
        String quote = postgres ? "\"" : "`";
        String table = quote + prefix + "warps" + quote;
        try (var translations = mockStatic(TranslationManager.class);
             var pools = mockConstruction(HikariDataSource.class, (pool, context) -> when(pool.getConnection()).thenAnswer(ignored -> DriverManager.getConnection(url, user, password)))) {
            DataStorage storage = DataStorage.create(options, Runnable::run, mock(PluginLogger.class));
            storage.initialize();
            try {
                WarpStore store = storage.warpStore();
                store.initialize().join();
                long insert = System.nanoTime();
                try (Connection connection = DriverManager.getConnection(url, user, password)) {
                    insertSql(connection, table, postgres, warps);
                }
                insert = System.nanoTime() - insert;
                this.report(type.name(), store, warps, insert);
            } finally {
                storage.close();
                try (Connection connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                    statement.execute("DROP TABLE IF EXISTS " + table);
                    statement.execute("DROP TABLE IF EXISTS " + quote + prefix + "meta" + quote);
                }
            }
        }
    }

    private void mongo(List<Warp> warps) throws Exception {
        PluginConfig.DatabaseOptions options = new PluginConfig.DatabaseOptions();
        String prefix = "bench_" + UUID.randomUUID().toString().replace("-", "") + "_";
        set(options.mongodb(), "collectionPrefix", prefix);
        DataStorage storage = DataStorage.create(options, Runnable::run, mock(PluginLogger.class));
        try {
            storage.initialize();
        } catch (RuntimeException exception) {
            System.out.println("MONGODB: skipped, " + exception.getMessage());
            return;
        }
        try (var client = MongoClients.create(MongoClientSettings.builder().applyConnectionString(new ConnectionString(options.mongodb().url())).uuidRepresentation(UuidRepresentation.STANDARD).build()); var translations = mockStatic(TranslationManager.class)) {
            WarpStore store = storage.warpStore();
            store.initialize().join();
            List<Document> documents = new ArrayList<>(warps.size());
            for (int i = 0; i < warps.size(); i++) {
                Warp warp = warps.get(i);
                WorldLocation location = warp.location();
                documents.add(new Document("_id", warp.id()).append("name_key", warp.key()).append("name", warp.name()).append("description", warp.description())
                        .append("server", warp.server()).append("world", location.world()).append("x", location.x()).append("y", location.y()).append("z", location.z())
                        .append("yaw", (double) location.yaw()).append("pitch", (double) location.pitch()).append("created_at", warp.createdAt()).append("updated_at", warp.updatedAt()));
            }
            var collection = client.getDatabase(options.mongodb().database()).getCollection(prefix + "warps");
            long insert = System.nanoTime();
            collection.insertMany(documents);
            insert = System.nanoTime() - insert;
            this.report("MONGODB", store, warps, insert);
        } finally {
            storage.close();
            try (var client = MongoClients.create(options.mongodb().url())) {
                client.getDatabase(options.mongodb().database()).getCollection(prefix + "warps").drop();
                client.getDatabase(options.mongodb().database()).getCollection(prefix + "meta").drop();
            }
        }
    }

    private void report(String name, WarpStore store, List<Warp> warps, long insertNanos) {
        long first = time(() -> assertEquals(COUNT, store.loadAll().join().size()));
        long[] runs = new long[5];
        for (int i = 0; i < runs.length; i++) {
            runs[i] = time(() -> store.loadAll().join());
        }
        Arrays.sort(runs);
        WarpRegistry registry = new WarpRegistry(store, "bench", message -> {});
        long registryLoad = time(registry::load);
        // 跨服同步时按 id 回查一条记录的往返耗时
        int lookups = 500;
        long find = time(() -> {
            for (int i = 0; i < lookups; i++) {
                store.find(warps.get(i * 17 % COUNT).id()).join();
            }
        }) / lookups;
        System.out.printf("%-10s insert=%dms loadAll first=%dms median=%dms registry.load=%dms find(id)=%.3fms%n",
                name, insertNanos / 1_000_000, first / 1_000_000, runs[2] / 1_000_000, registryLoad / 1_000_000, find / 1_000_000.0);
    }

    // 与数据库无关的部分: 建索引、补全、同步消息
    private void memory(List<Warp> warps) {
        WarpRegistry registry = new WarpRegistry(new PreloadedStore(warps), "bench", message -> {});
        long build = time(registry::load);
        int rounds = 100_000;
        long emptyPrefix = time(() -> {
            for (int i = 0; i < rounds; i++) registry.complete("", warp -> true, LIMIT);
        }) / rounds;
        long shortPrefix = time(() -> {
            for (int i = 0; i < rounds; i++) registry.complete("warp_01", warp -> true, LIMIT);
        }) / rounds;
        long lookup = time(() -> {
            for (int i = 0; i < rounds; i++) registry.get(warps.get(i % COUNT).name());
        }) / rounds;
        Warp sample = warps.get(COUNT / 2);
        int messageSize = encode(WarpMessage.save("bench", sample)).readableBytes();
        long codec = time(() -> {
            for (int i = 0; i < rounds; i++) {
                ByteBuf buffer = encode(WarpMessage.save("bench", sample));
                WarpMessage.CODEC.decode(buffer);
            }
        }) / rounds;
        // 交替修改描述和改名, 取平均
        int changes = 1000;
        long rebuild = time(() -> {
            for (int i = 0; i < changes; i++) {
                String name = i % 2 == 0 ? sample.name() : "renamed_" + i;
                registry.accept(WarpMessage.save("other", new Warp(sample.id(), name, "changed " + i, sample.server(), sample.location(), null, 1, 10_000L + i)));
            }
        }) / changes;
        // 补全封包里每个候选是一段带长度前缀的 UTF-8 字符串
        long allBytes = 0;
        for (int i = 0; i < warps.size(); i++) allBytes += warps.get(i).name().getBytes(StandardCharsets.UTF_8).length + 1;
        long cappedBytes = 0;
        List<String> capped = registry.complete("", warp -> true, LIMIT);
        for (int i = 0; i < capped.size(); i++) cappedBytes += capped.get(i).getBytes(StandardCharsets.UTF_8).length + 1;
        System.out.printf("MEMORY     build=%dms get=%dns complete(\"\")=%.1fus complete(\"warp_01\")=%.1fus rebuild-after-change=%.2fms%n",
                build / 1_000_000, lookup, emptyPrefix / 1000.0, shortPrefix / 1000.0, rebuild / 1_000_000.0);
        System.out.printf("MESSAGE    size=%dB encode+decode=%.2fus%n", messageSize, codec / 1000.0);
        System.out.printf("SUGGEST    all %d names=%.1fKB, first %d names=%.1fKB%n", COUNT, allBytes / 1024.0, LIMIT, cappedBytes / 1024.0);
    }

    private static List<Warp> generate() {
        List<Warp> warps = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            WorldLocation location = new WorldLocation(i % 3 == 0 ? "world_nether" : "world", i * 1.5, 64 + i % 100, -i * 2.25, i % 360, 0);
            warps.add(new Warp(UUID.randomUUID(), String.format("warp_%05d", i), "Benchmark warp number " + i + " near the spawn area",
                    "server-" + (i % 4), location, i % 2 == 0 ? UUID.randomUUID() : null, 1000 + i, 1000 + i));
        }
        return warps;
    }

    private static void insertSql(Connection connection, String table, boolean postgres, List<Warp> warps) throws SQLException {
        String sql = "INSERT INTO " + table + " (id, name_key, name, description, server, world, x, y, z, yaw, pitch, creator, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < warps.size(); i++) {
                Warp warp = warps.get(i);
                WorldLocation location = warp.location();
                setUuid(statement, 1, warp.id(), postgres);
                statement.setString(2, warp.key());
                statement.setString(3, warp.name());
                statement.setString(4, warp.description());
                statement.setString(5, warp.server());
                statement.setString(6, location.world());
                statement.setDouble(7, location.x());
                statement.setDouble(8, location.y());
                statement.setDouble(9, location.z());
                statement.setFloat(10, location.yaw());
                statement.setFloat(11, location.pitch());
                setUuid(statement, 12, warp.creator(), postgres);
                statement.setLong(13, warp.createdAt());
                statement.setLong(14, warp.updatedAt());
                statement.addBatch();
                if (i % 1000 == 999) statement.executeBatch();
            }
            statement.executeBatch();
        }
        connection.commit();
    }

    private static void setUuid(PreparedStatement statement, int index, UUID uuid, boolean postgres) throws SQLException {
        if (uuid == null) {
            statement.setNull(index, postgres ? java.sql.Types.OTHER : java.sql.Types.BINARY);
        } else if (postgres) {
            statement.setObject(index, uuid);
        } else {
            statement.setBytes(index, UUIDUtils.toBytes(uuid));
        }
    }

    private static ByteBuf encode(WarpMessage message) {
        message.setTargetServer("");
        ByteBuf buffer = Unpooled.buffer();
        WarpMessage.CODEC.encode(buffer, message);
        return buffer;
    }

    private static long time(Runnable task) {
        long start = System.nanoTime();
        task.run();
        return System.nanoTime() - start;
    }

    // 字段可能声明在父类上
    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    // 只返回预先生成的数据, 用于单独测量内存部分
    private record PreloadedStore(List<Warp> warps) implements WarpStore {
        @Override
        public CompletableFuture<Void> initialize() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<Warp>> loadAll() {
            return CompletableFuture.completedFuture(this.warps);
        }

        @Override
        public CompletableFuture<Optional<Warp>> find(UUID id) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Optional<Warp>> findByName(String name) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Boolean> save(Warp warp) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> delete(UUID id) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Integer> deleteByWorld(String server, String world) {
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public CompletableFuture<Integer> deleteByServer(String server) {
            return CompletableFuture.completedFuture(0);
        }
    }
}

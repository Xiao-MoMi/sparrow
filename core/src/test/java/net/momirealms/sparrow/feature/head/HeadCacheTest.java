package net.momirealms.sparrow.feature.head;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeadCacheTest {
    @Test
    void sharesDataAcrossInstancesAndRedisReadsDoNotRenewItsLifetime() throws Exception {
        RedisClient client = RedisClient.create("redis://localhost:6379/15");
        try (StatefulRedisConnection<byte[], byte[]> connection = client.connect(ByteArrayCodec.INSTANCE)) {
            HeadSettings settings = new HeadSettings();
            String endpoint = "https://" + UUID.randomUUID() + ".test/{name}";
            set(settings.api(), "nameUrl", endpoint);
            set(settings.cache().memory(), "ttl", "50ms");
            set(settings.cache().redis(), "ttl", "2s");
            String prefix = "sparrow:head:v1:" + HeadCache.digest(endpoint + "\n" + settings.api().profileUrl() + "\n{}") + ":";
            byte[] key = (prefix + "name:tester").getBytes(StandardCharsets.UTF_8);
            try {
                HeadCache first = new HeadCache(settings, connection, mock(PluginLogger.class));
                HeadCache second = new HeadCache(settings, connection, mock(PluginLogger.class));
                HeadData original = new HeadData(UUID.randomUUID(), "Tester", "texture", "signature");
                first.put(List.of("name:tester"), original).get();
                assertEquals(original, second.get("name:tester"));
                long initial = connection.sync().pttl(key);
                assertTrue(initial > 0 && initial <= 2000);
                Thread.sleep(100);
                assertEquals(original, second.get("name:tester"));
                assertTrue(connection.sync().pttl(key) < initial);
                connection.sync().del(key);
                assertNull(second.get("name:tester"));
                assertNull(first.get("name:tester"));
            } finally {
                connection.sync().del(key);
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    void differentEndpointsNeverReuseEachOthersMemoryOrRedisEntries() throws Exception {
        RedisClient client = RedisClient.create("redis://localhost:6379/15");
        try (StatefulRedisConnection<byte[], byte[]> connection = client.connect(ByteArrayCodec.INSTANCE)) {
            HeadSettings firstSettings = new HeadSettings();
            String endpoint = "https://" + UUID.randomUUID() + ".test/{name}";
            set(firstSettings.api(), "nameUrl", endpoint);
            HeadSettings secondSettings = new HeadSettings();
            set(secondSettings.api(), "nameUrl", endpoint + "/other");
            byte[] key = ("sparrow:head:v1:" + HeadCache.digest(endpoint + "\n" + firstSettings.api().profileUrl() + "\n{}") + ":name:tester").getBytes(StandardCharsets.UTF_8);
            try {
                HeadCache first = new HeadCache(firstSettings, connection, mock(PluginLogger.class));
                HeadCache second = new HeadCache(secondSettings, connection, mock(PluginLogger.class));
                first.put(List.of("name:tester"), new HeadData(UUID.randomUUID(), "Tester", "texture", null)).get();
                assertNull(second.get("name:tester"));
            } finally {
                connection.sync().del(key);
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    void unavailableRedisDegradesToMissAndDoesNotLoseSuccessfulMemoryWrites() throws Exception {
        HeadSettings settings = new HeadSettings();
        StatefulRedisConnection<byte[], byte[]> connection = mock(StatefulRedisConnection.class);
        when(connection.async()).thenThrow(new IllegalStateException("offline"));
        PluginLogger logger = mock(PluginLogger.class);
        HeadCache cache = new HeadCache(settings, connection, logger);
        assertNull(cache.get("name:tester"));
        HeadData data = new HeadData(UUID.randomUUID(), "Tester", "texture", null);
        cache.put(List.of("name:tester"), data).get();
        assertEquals(data, cache.get("name:tester"));
        verify(logger, times(2)).warn(anyString(), any(Throwable.class));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

package net.momirealms.sparrow.feature.head;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class HeadCache {
    private final Cache<String, Entry> memory;
    private final StatefulRedisConnection<byte[], byte[]> redis;
    private final PluginLogger logger;
    private final long memoryTtl;
    private final long redisTtl;
    private final long timeoutMillis;
    private final String profilePrefix;

    HeadCache(HeadSettings settings, @Nullable StatefulRedisConnection<byte[], byte[]> redis, PluginLogger logger) {
        HeadSettings.MemoryOptions memory = settings.cache().memory();
        HeadSettings.RedisOptions shared = settings.cache().redis();
        this.memoryTtl = memory.enabled() ? DurationUtils.parsePositive(memory.ttl()).toMillis() : 0;
        this.redisTtl = shared.enabled() ? DurationUtils.parsePositive(shared.ttl()).toMillis() : 0;
        this.memory = Caffeine.newBuilder().maximumSize(memory.enabled() ? memory.maxSize() : 0)
                .expireAfterWrite(Duration.ofMillis(Math.max(1, this.memoryTtl))).build();
        this.redis = redis;
        this.logger = logger;
        this.timeoutMillis = DurationUtils.parsePositive(settings.api().requestTimeout()).toMillis();
        this.profilePrefix = "sparrow:head:v1:" + digest(settings.api().nameUrl() + "\n" + settings.api().profileUrl() + "\n" + GsonHelper.get().toJson(new TreeMap<>(settings.api().headers()))) + ":";
    }

    @Nullable
    HeadData get(String key) throws InterruptedException {
        long now = System.currentTimeMillis();
        Entry local = this.memory.getIfPresent(key);
        if (local != null && now < local.expiresAt && now - local.fetchedAt < this.memoryTtl) return local.data;
        if (this.redis == null) return null;
        try {
            byte[] bytes = this.redis.async().get(this.key(key)).get(this.timeoutMillis, TimeUnit.MILLISECONDS);
            if (bytes == null) return null;
            Entry entry = GsonHelper.get().fromJson(new String(bytes, StandardCharsets.UTF_8), Entry.class);
            if (entry == null || entry.data == null || entry.data.uuid() == null || entry.data.name() == null || entry.data.texture() == null) {
                throw new IllegalArgumentException("Invalid head cache entry");
            }
            now = System.currentTimeMillis();
            if (now >= entry.expiresAt || now - entry.fetchedAt >= this.redisTtl) return null;
            entry = new Entry(entry.data, entry.fetchedAt, Math.min(entry.expiresAt, entry.fetchedAt + this.redisTtl));
            if (now - entry.fetchedAt < this.memoryTtl) this.memory.put(key, entry);
            return entry.data;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            this.logger.warn("Could not read the head Redis cache; querying the head service", exception);
            return null;
        }
    }

    // 在模块检查请求有效性之后提交写入, 返回的 Future 只等待 Redis 应答.
    CompletableFuture<Void> put(List<String> keys, HeadData data) {
        long now = System.currentTimeMillis();
        Entry entry = new Entry(data, now, now + (this.redis != null ? this.redisTtl : this.memoryTtl));
        byte[] value = GsonHelper.get().toJson(entry).getBytes(StandardCharsets.UTF_8);
        CompletableFuture<?>[] writes = new CompletableFuture<?>[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (this.memoryTtl > 0) this.memory.put(key, entry);
            try {
                writes[i] = this.redis == null ? CompletableFuture.completedFuture(null)
                        : this.redis.async().set(this.key(key), value, SetArgs.Builder.px(this.redisTtl)).toCompletableFuture();
            } catch (RuntimeException exception) {
                writes[i] = CompletableFuture.failedFuture(exception);
            }
        }
        return CompletableFuture.allOf(writes).orTimeout(this.timeoutMillis, TimeUnit.MILLISECONDS).exceptionally(exception -> {
            this.logger.warn("Could not write the head Redis cache; the fetched head is still available", exception);
            return null;
        });
    }

    private byte[] key(String query) {
        return (this.profilePrefix + query).getBytes(StandardCharsets.UTF_8);
    }

    void clear() {
        this.memory.invalidateAll();
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private record Entry(HeadData data, long fetchedAt, long expiresAt) {
    }
}

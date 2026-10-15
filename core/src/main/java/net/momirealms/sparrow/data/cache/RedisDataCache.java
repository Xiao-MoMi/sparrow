package net.momirealms.sparrow.data.cache;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.data.message.DataCacheInvalidationMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

final class RedisDataCache<K, V> implements AutoCloseable {
    // KEYS 为标记表和数据表, READ 的 ARGV 为条目键与候选标记.
    private static final String READ = """
            local token = redis.call('HGET', KEYS[1], ARGV[1])
            if not token then
                token = ARGV[2]
                redis.call('HSET', KEYS[1], ARGV[1], token)
            end
            return {token, redis.call('HGET', KEYS[2], ARGV[1])}
            """;
    // ARGV 为条目键, 读取时的标记和编码结果. 标记匹配时写入数据.
    private static final String FILL = """
            if redis.call('HGET', KEYS[1], ARGV[1]) ~= ARGV[2] then
                return 0
            end
            redis.call('HSET', KEYS[2], ARGV[1], ARGV[3])
            return 1
            """;
    // ARGV 为范围, 频道, 消息及条目键. 删除与广播在同一脚本内执行.
    private static final String INVALIDATE = """
            if ARGV[1] == 'all' then
                redis.call('UNLINK', KEYS[1], KEYS[2])
            else
                for i = 4, #ARGV do
                    redis.call('HDEL', KEYS[1], ARGV[i])
                    redis.call('HDEL', KEYS[2], ARGV[i])
                end
            end
            return redis.call('PUBLISH', ARGV[2], ARGV[3])
            """;

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final CacheDefinition<K, V> definition;
    private final String storagePrefix;            // 数据库配置, 数据 id 和插件版本共同确定的存储范围
    private final UUID origin = UUID.randomUUID(); // 每个缓存实例独立的消息来源, 用于识别自身广播
    private final byte[][] keys;
    private boolean closed;

    RedisDataCache(CacheDefinition<K, V> definition) {
        this.definition = definition;
        String database = UUID.nameUUIDFromBytes(databaseIdentity().getBytes(StandardCharsets.UTF_8)).toString();
        String domain = Base64.getUrlEncoder().withoutPadding().encodeToString(definition.id().toString().getBytes(StandardCharsets.UTF_8));
        this.storagePrefix = "sparrow:cache:v1:{" + this.plugin.redisConnector().database() + ":" + database + ":" + domain + ":" + this.plugin.pluginVersion() + "}";
        this.keys = new byte[][] {(this.storagePrefix + ":tokens").getBytes(StandardCharsets.UTF_8), (this.storagePrefix + ":values").getBytes(StandardCharsets.UTF_8)};
    }

    // 取当前启用数据库的连接地址与表或集合前缀.
    private static String databaseIdentity() {
        PluginConfig.DatabaseOptions options = PluginConfig.database();
        return switch (options.type()) {
            case MYSQL -> "mysql\n" + options.mysql().url() + "\n" + options.mysql().tablePrefix();
            case MARIADB -> "mariadb\n" + options.mariadb().url() + "\n" + options.mariadb().tablePrefix();
            case POSTGRESQL -> "postgresql\n" + options.postgresql().url() + "\n" + options.postgresql().tablePrefix();
            case MONGODB -> "mongodb\n" + options.mongodb().url() + "\n" + options.mongodb().database() + "\n" + options.mongodb().collectionPrefix();
        };
    }

    // Redis 命中时解码返回, 未命中时调用原始数据源并尝试回填.
    @NotNull
    CompletableFuture<V> load(K key) {
        byte[] field = this.definition.encodeKey(key).getBytes(StandardCharsets.UTF_8);
        byte[] candidate = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        CompletableFuture<List<Object>> lookup;
        synchronized (this) {
            if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Shared cache is closed"));
            lookup = this.redis().<List<Object>>eval(READ, ScriptOutputType.MULTI, this.keys, field, candidate).toCompletableFuture();
        }
        return lookup.thenCompose(result -> {
            // 返回值依次为标记和缓存字节, 第二项为 null 时回源.
            byte[] value = (byte[]) result.get(1);
            if (value != null) return CompletableFuture.completedFuture(this.definition.decodeValue(value));
            byte[] token = (byte[]) result.getFirst();
            return this.definition.load(key).thenCompose(loaded -> this.storeIfCurrent(field, token, loaded));
        });
    }

    // 携带读取标记回填. 标记失效时仍将结果返回原请求.
    private CompletableFuture<V> storeIfCurrent(byte[] field, byte[] token, V value) {
        synchronized (this) {
            if (this.closed) return CompletableFuture.completedFuture(value);
            return this.redis().<Long>eval(FILL, ScriptOutputType.INTEGER, this.keys, field, token, this.definition.encodeValue(value))
                    .toCompletableFuture()
                    .thenApply(ignored -> value);
        }
    }

    // 提交共享失效并等待 Redis 删除与广播完成, invalidated 为 null 时作用于整个缓存域.
    @NotNull
    CompletableFuture<Void> invalidate(@Nullable List<K> invalidated) {
        List<String> encoded = invalidated == null ? null : invalidated.stream().map(this.definition::encodeKey).toList();
        DataCacheInvalidationMessage message = new DataCacheInvalidationMessage(this.definition.id(), this.storagePrefix, this.origin, encoded);
        MessageBroker<FriendlyByteBuf> broker = this.plugin.messageBrokerManager().broker();
        byte[][] arguments = new byte[3 + (encoded == null ? 0 : encoded.size())][];
        arguments[0] = (encoded == null ? "all" : "keys").getBytes(StandardCharsets.UTF_8);
        arguments[1] = broker.channel();
        arguments[2] = broker.encode(message);
        if (encoded != null) {
            for (int i = 0, size = encoded.size(); i < size; i++) {
                arguments[i + 3] = encoded.get(i).getBytes(StandardCharsets.UTF_8);
            }
        }
        synchronized (this) {
            if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Shared cache is closed"));
            return this.redis().<Long>eval(INVALIDATE, ScriptOutputType.INTEGER, this.keys, arguments)
                    .toCompletableFuture()
                    .thenApply(ignored -> null);
        }
    }

    @NotNull
    K decodeKey(String key) {
        return this.definition.decodeKey(key);
    }

    private RedisAsyncCommands<byte[], byte[]> redis() {
        return this.plugin.redisConnector().connection().async();
    }

    @Override
    public synchronized void close() {
        this.closed = true;
    }

    @NotNull
    String storagePrefix() {
        return this.storagePrefix;
    }

    @NotNull
    UUID origin() {
        return this.origin;
    }
}
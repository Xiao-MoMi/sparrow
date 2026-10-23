package net.momirealms.sparrow.data.internal;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.momirealms.sparrow.data.definition.DataDefinition;
import net.momirealms.sparrow.data.message.DataChangedMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

// 每个 key 一个 Hash, 字段 "" 保存 key 的版本, 同时表示这个 key 已从数据库装入
final class RedisDataBackend<E, V> {
    private static final byte[] EMPTY = new byte[0];
    private static final int RENEW_BATCH_SIZE = 256;
    private static final String EXPIRY = """
            local function touch(entries, dirty, saving, ttl)
                if ttl == '0' or redis.call('EXISTS', dirty) == 1 or redis.call('EXISTS', saving) == 1 then
                    redis.call('PERSIST', entries)
                else
                    redis.call('PEXPIRE', entries, ttl)
                end
            end
            """;
    private static final String READ = EXPIRY + """
            touch(KEYS[1], KEYS[3], KEYS[4], ARGV[1])
            return redis.call('HGETALL', KEYS[1])
            """;
    private static final String INITIALIZE = EXPIRY + """
            if redis.call('HEXISTS', KEYS[1], '') == 0 then
                for i = 2, #ARGV, 2 do
                    redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
                end
                redis.call('HSET', KEYS[1], '', '0')
            end
            touch(KEYS[1], KEYS[3], KEYS[4], ARGV[1])
            return redis.call('HGETALL', KEYS[1])
            """;
    // ARGV 依次为期望版本, 频道, 通知前缀, 编码后的 key, 写入条数, TTL, 写入的条目与值, 删除的条目
    private static final String COMMIT = EXPIRY + """
            local version = redis.call('HGET', KEYS[1], '')
            if not version then
                return redis.error_reply('Data entries are not loaded')
            end
            if ARGV[1] ~= '' and version ~= ARGV[1] then
                touch(KEYS[1], KEYS[3], KEYS[4], ARGV[6])
                return {0, redis.call('HGETALL', KEYS[1])}
            end
            if #ARGV == 6 then
                touch(KEYS[1], KEYS[3], KEYS[4], ARGV[6])
                return {1, version}
            end
            redis.call('PERSIST', KEYS[1])
            local puts = 6 + 2 * tonumber(ARGV[5])
            for i = 7, puts, 2 do
                redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
                redis.call('SADD', KEYS[3], ARGV[i])
            end
            for i = puts + 1, #ARGV do
                redis.call('HDEL', KEYS[1], ARGV[i])
                redis.call('SADD', KEYS[3], ARGV[i])
            end
            redis.call('HINCRBY', KEYS[1], '', 1)
            version = redis.call('HGET', KEYS[1], '')
            redis.call('SADD', KEYS[2], ARGV[4])
            redis.call('PUBLISH', ARGV[2], ARGV[3] .. string.char(#version) .. version)
            return {1, version}
            """;
    // saving 集合存在时表示其他实例正在保存这个 key
    private static final String TAKE_SAVE = EXPIRY + """
            touch(KEYS[1], KEYS[3], KEYS[4], ARGV[1])
            if redis.call('EXISTS', KEYS[4]) == 1 then
                return {0}
            end
            if redis.call('EXISTS', KEYS[3]) == 0 then
                return {1}
            end
            redis.call('RENAME', KEYS[3], KEYS[4])
            local result = {1}
            for _, entry in ipairs(redis.call('SMEMBERS', KEYS[4])) do
                result[#result + 1] = entry
                result[#result + 1] = redis.call('HGET', KEYS[1], entry)
            end
            return result
            """;
    private static final String FINISH_SAVE = EXPIRY + """
            redis.call('DEL', KEYS[4])
            if redis.call('EXISTS', KEYS[3]) == 0 then
                redis.call('SREM', KEYS[2], ARGV[1])
            end
            touch(KEYS[1], KEYS[3], KEYS[4], ARGV[2])
            return 1
            """;
    private static final String RESTORE_SAVE = """
            redis.call('SUNIONSTORE', KEYS[3], KEYS[3], KEYS[4])
            redis.call('DEL', KEYS[4])
            redis.call('PERSIST', KEYS[1])
            return 1
            """;
    private static final String RENEW = EXPIRY + """
            for i = 1, #KEYS, 3 do
                touch(KEYS[i], KEYS[i + 1], KEYS[i + 2], ARGV[1])
            end
            return 1
            """;

    private final DataDefinition<E, V> definition;
    private final RedisAsyncCommands<byte[], byte[]> commands;
    private final Executor waiting;
    private final byte[] channel;
    private final byte[] messageHeader;
    private final byte[] dirtyKey;
    private final byte[] entriesPrefix;
    private final byte[] dirtyPrefix;
    private final byte[] savingPrefix;
    private final byte[] ttl;

    RedisDataBackend(@NotNull DataDefinition<E, V> definition) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        String prefix = "sparrow:data:" + definition.id() + ":";
        this.definition = definition;
        this.commands = plugin.redisConnector().connection().async();
        this.waiting = CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS, plugin.scheduler().async());
        this.channel = plugin.messageBrokerManager().broker().channel();
        this.messageHeader = plugin.dataAccess().messageHeader();
        this.dirtyKey = (prefix + "dirty").getBytes(StandardCharsets.UTF_8);
        this.entriesPrefix = (prefix + "entries:").getBytes(StandardCharsets.UTF_8);
        this.dirtyPrefix = (prefix + "dirty:").getBytes(StandardCharsets.UTF_8);
        this.savingPrefix = (prefix + "saving:").getBytes(StandardCharsets.UTF_8);
        this.ttl = Long.toString(definition.redisTtl() == null ? 0 : definition.redisTtl().toMillis()).getBytes(StandardCharsets.US_ASCII);
    }

    @NotNull
    CompletableFuture<LocalEntries<E, V>> load(@NotNull UUID key) {
        byte[][] keys = this.keys(UUIDUtils.toBytes(key));
        return this.commands.<List<Object>>eval(READ, ScriptOutputType.MULTI, keys, this.ttl)
                .toCompletableFuture()
                .thenCompose(stored -> {
                    LocalEntries<E, V> entries = this.decode(stored);
                    if (entries != null) return CompletableFuture.completedFuture(entries);
                    // 尚未装入时回源数据库, 多个实例同时装入时先到的一方生效
                    return this.definition.store().load(key).thenCompose(loaded -> {
                        byte[][] arguments = new byte[1 + loaded.size() * 2][];
                        arguments[0] = this.ttl;
                        int index = 1;
                        for (Map.Entry<E, V> entry : loaded.entrySet()) {
                            arguments[index++] = this.definition.entryCodec().encode(entry.getKey());
                            arguments[index++] = this.definition.valueCodec().encode(entry.getValue());
                        }
                        return this.commands.<List<Object>>eval(INITIALIZE, ScriptOutputType.MULTI, keys, arguments)
                                .toCompletableFuture()
                                .thenApply(this::decode);
                    });
                });
    }

    // expectedVersion 小于 0 时不比较版本. changes 中的 null 表示删除, changes 为空时只校验版本
    @NotNull
    CompletableFuture<Commit<E, V>> commit(@NotNull UUID key, long expectedVersion, @NotNull Map<E, V> changes) {
        byte[] encodedKey = UUIDUtils.toBytes(key);
        int removals = 0;
        for (V value : changes.values()) {
            if (value == null) {
                removals++;
            }
        }
        int puts = changes.size() - removals;
        byte[][] arguments = new byte[6 + 2 * puts + removals][];
        byte[][] entries = new byte[changes.size()][];
        byte[][] values = new byte[changes.size()][];
        int index = 0;
        int put = 6;
        int removal = 6 + 2 * puts;
        for (Map.Entry<E, V> change : changes.entrySet()) {
            byte[] entry = this.definition.entryCodec().encode(change.getKey());
            byte[] value = change.getValue() == null ? null : this.definition.valueCodec().encode(change.getValue());
            entries[index] = entry;
            values[index++] = value;
            if (value == null) {
                arguments[removal++] = entry;
            } else {
                arguments[put++] = entry;
                arguments[put++] = value;
            }
        }
        byte[] notification = EMPTY;
        if (!changes.isEmpty()) {
            ByteBuf buffer = Unpooled.buffer();
            try {
                buffer.writeBytes(this.messageHeader);
                DataChangedMessage.writePrefix(buffer, this.definition.id(), encodedKey, entries, values);
                notification = ByteBufUtil.getBytes(buffer);
            } finally {
                buffer.release();
            }
        }
        arguments[0] = expectedVersion < 0 ? EMPTY : Long.toString(expectedVersion).getBytes(StandardCharsets.US_ASCII);
        arguments[1] = this.channel;
        arguments[2] = notification;
        arguments[3] = encodedKey;
        arguments[4] = Integer.toString(puts).getBytes(StandardCharsets.US_ASCII);
        arguments[5] = this.ttl;
        return this.commands.<List<Object>>eval(COMMIT, ScriptOutputType.MULTI, this.keys(encodedKey), arguments)
                .toCompletableFuture()
                .thenApply(result -> {
                    if ((Long) result.getFirst() == 1L) return new Commit<>(parseVersion((byte[]) result.get(1)), null);
                    @SuppressWarnings("unchecked")
                    LocalEntries<E, V> current = this.decode((List<Object>) result.get(1));
                    return new Commit<>(current.version, current);
                });
    }

    @NotNull
    CompletableFuture<List<UUID>> dirtyKeys() {
        return this.commands.smembers(this.dirtyKey)
                .toCompletableFuture()
                .thenApply(members -> {
                    List<UUID> keys = new ArrayList<>(members.size());
                    for (byte[] member : members) {
                        keys.add(UUIDUtils.fromBytes(member));
                    }
                    return keys;
                });
    }

    // 保存调用前已提交到 Redis 的修改. 保存期间的新修改留在 dirty 中, 失败时取出的条目并回 dirty
    @NotNull
    CompletableFuture<Void> save(@NotNull UUID key) {
        byte[] encodedKey = UUIDUtils.toBytes(key);
        return this.save(key, encodedKey, this.keys(encodedKey));
    }

    private CompletableFuture<Void> save(UUID key, byte[] encodedKey, byte[][] keys) {
        return this.commands.<List<Object>>eval(TAKE_SAVE, ScriptOutputType.MULTI, keys, this.ttl)
                .toCompletableFuture()
                .thenCompose(taken -> {
                    if ((Long) taken.getFirst() == 0L) {
                        return CompletableFuture.completedFuture(key).thenComposeAsync(ignored -> this.save(key, encodedKey, keys), this.waiting);
                    }
                    int size = taken.size();
                    if (size == 1) return CompletableFuture.completedFuture(null);
                    Map<E, V> upserts = new HashMap<>();
                    Set<E> removals = new HashSet<>();
                    for (int i = 1; i < size; i += 2) {
                        E entry = this.definition.entryCodec().decode((byte[]) taken.get(i));
                        byte[] value = (byte[]) taken.get(i + 1);
                        if (value == null) {
                            removals.add(entry);
                        } else {
                            upserts.put(entry, this.definition.valueCodec().decode(value));
                        }
                    }
                    return this.definition.store()
                            .save(key, upserts, removals)
                            .thenCompose(ignored -> this.commands.<Long>eval(FINISH_SAVE, ScriptOutputType.INTEGER, keys, encodedKey, this.ttl).toCompletableFuture())
                            .exceptionallyCompose(error -> this.commands.<Long>eval(RESTORE_SAVE, ScriptOutputType.INTEGER, keys)
                                    .toCompletableFuture()
                                    .thenCompose(ignored -> CompletableFuture.failedFuture(error)))
                            .thenApply(ignored -> null);
                });
    }

    @NotNull
    CompletableFuture<Void> renew(@NotNull Collection<UUID> selected) {
        List<byte[]> keys = new ArrayList<>(RENEW_BATCH_SIZE * 3);
        List<CompletableFuture<?>> batches = new ArrayList<>();
        for (UUID key : selected) {
            byte[] encoded = UUIDUtils.toBytes(key);
            keys.add(concat(this.entriesPrefix, encoded));
            keys.add(concat(this.dirtyPrefix, encoded));
            keys.add(concat(this.savingPrefix, encoded));
            if (keys.size() == RENEW_BATCH_SIZE * 3) {
                batches.add(this.renewBatch(keys));
                keys.clear();
            }
        }
        if (!keys.isEmpty()) {
            batches.add(this.renewBatch(keys));
        }
        return CompletableFuture.allOf(batches.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Long> renewBatch(List<byte[]> keys) {
        return this.commands.<Long>eval(RENEW, ScriptOutputType.INTEGER, keys.toArray(byte[][]::new), this.ttl).toCompletableFuture();
    }

    private byte[][] keys(byte[] encodedKey) {
        return new byte[][] {
                concat(this.entriesPrefix, encodedKey),
                this.dirtyKey,
                concat(this.dirtyPrefix, encodedKey),
                concat(this.savingPrefix, encodedKey)
        };
    }

    // 解码 HGETALL 结果. 没有版本字段时表示这个 key 尚未装入, 返回 null
    @Nullable
    private LocalEntries<E, V> decode(List<Object> stored) {
        LocalEntries<E, V> entries = new LocalEntries<>(-1);
        int size = stored.size();
        for (int i = 0; i < size; i += 2) {
            byte[] field = (byte[]) stored.get(i);
            byte[] value = (byte[]) stored.get(i + 1);
            if (field.length == 0) {
                entries.version = parseVersion(value);
            } else {
                entries.entries.put(this.definition.entryCodec().decode(field), this.definition.valueCodec().decode(value));
            }
        }
        return entries.version < 0 ? null : entries;
    }

    private static long parseVersion(byte[] version) {
        return Long.parseLong(new String(version, StandardCharsets.US_ASCII));
    }

    private static byte[] concat(byte[] prefix, byte[] key) {
        byte[] result = Arrays.copyOf(prefix, prefix.length + key.length);
        System.arraycopy(key, 0, result, prefix.length, key.length);
        return result;
    }

    // current 不为 null 时表示版本不符, 它是 Redis 中的最新状态
    record Commit<E, V>(long version, @Nullable LocalEntries<E, V> current) {
    }
}
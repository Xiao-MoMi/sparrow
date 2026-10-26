package net.momirealms.sparrow.feature.tpa;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import net.momirealms.sparrow.plugin.SparrowPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TpaStore {
    private static final String PREFIX = "sparrow:tpa:v2:";
    private static final long CLEANUP_GRACE = 10_000L;
    // 请求、两个索引与发送冷却一起写入; 索引过期时间取其中最晚的一条.
    private static final String CREATE = """
            local now = tonumber(ARGV[1])
            local expires = tonumber(ARGV[2])
            if redis.call('ZSCORE', KEYS[2], ARGV[4]) then
                local old = tonumber(redis.call('ZSCORE', KEYS[2], ARGV[4]))
                if old > now and redis.call('EXISTS', KEYS[1]) == 1 then return {0, 0} end
            end
            if ARGV[8] == '0' then
                local denied = redis.call('PTTL', KEYS[5])
                if denied > 0 then return {-1, denied} end
                if tonumber(ARGV[7]) > 0 then
                    local cooldown = redis.call('PTTL', KEYS[4])
                    if cooldown > 0 then return {-2, cooldown} end
                end
            end
            redis.call('SET', KEYS[1], ARGV[3], 'PXAT', expires + tonumber(ARGV[6]))
            redis.call('ZADD', KEYS[2], expires, ARGV[4])
            redis.call('ZADD', KEYS[3], expires, ARGV[5])
            for i = 2, 3 do
                local last = redis.call('ZREVRANGE', KEYS[i], 0, 0, 'WITHSCORES')
                redis.call('PEXPIREAT', KEYS[i], tonumber(last[2]) + tonumber(ARGV[6]))
            end
            if ARGV[8] == '0' and tonumber(ARGV[7]) > 0 then
                redis.call('SET', KEYS[4], '1', 'PX', ARGV[7])
            end
            return {1, 0}
            """;
    // 比较完整记录中的独立请求 ID, 旧回调不能消费同一对玩家的新请求.
    private static final String TAKE = """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            local expired = tonumber(ARGV[2]) <= tonumber(ARGV[3])
            if (ARGV[4] == '1') ~= expired then return 0 end
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[5])
            redis.call('ZREM', KEYS[3], ARGV[6])
            if tonumber(ARGV[7]) > 0 then redis.call('SET', KEYS[4], '1', 'PX', ARGV[7]) end
            return 1
            """;
    private static final String LIST = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            return redis.call('ZREVRANGE', KEYS[1], 0, -1)
            """;

    private final RedisAsyncCommands<byte[], byte[]> redis = SparrowPlugin.instance().redisConnector().connection().async();

    CompletableFuture<CreateResult> create(TpaRequest request, long cooldownMillis, boolean bypass) {
        return this.redis.<List<Object>>eval(
                CREATE,
                ScriptOutputType.MULTI,
                new byte[][]{
                        requestKey(request.requester(), request.target()),
                        incomingKey(request.target()),
                        outgoingKey(request.requester()),
                        bytes(PREFIX + "cooldown:" + request.requester()),
                        deniedKey(request.requester(), request.target())
                },
                bytes(Long.toString(System.currentTimeMillis())),
                bytes(Long.toString(request.expiresAt())),
                request.encode(),
                bytes(request.requester().toString()),
                bytes(request.target().toString()),
                bytes(Long.toString(CLEANUP_GRACE)),
                bytes(Long.toString(cooldownMillis)),
                bytes(bypass ? "1" : "0")
        ).toCompletableFuture().thenApply(result -> new CreateResult(((Number) result.get(0)).intValue(), ((Number) result.get(1)).longValue()));
    }

    CompletableFuture<Boolean> take(TpaRequest request, boolean expired, long denyMillis) {
        return this.redis.<Long>eval(
                TAKE,
                ScriptOutputType.INTEGER,
                new byte[][]{
                        requestKey(request.requester(), request.target()),
                        incomingKey(request.target()),
                        outgoingKey(request.requester()),
                        deniedKey(request.requester(), request.target())
                },
                request.encode(),
                bytes(Long.toString(request.expiresAt())),
                bytes(Long.toString(System.currentTimeMillis())),
                bytes(expired ? "1" : "0"),
                bytes(request.requester().toString()),
                bytes(request.target().toString()),
                bytes(Long.toString(denyMillis))
        ).toCompletableFuture().thenApply(taken -> taken == 1);
    }

    CompletableFuture<List<TpaRequest>> list(UUID player, boolean incoming) {
        return this.redis.<List<byte[]>>eval(
                LIST,
                ScriptOutputType.MULTI,
                new byte[][]{incoming ? incomingKey(player) : outgoingKey(player)},
                bytes(Long.toString(System.currentTimeMillis()))
        ).toCompletableFuture().thenCompose(members -> {
            List<CompletableFuture<byte[]>> values = new ArrayList<>(members.size());
            for (int i = 0, size = members.size(); i < size; i++) {
                UUID other = UUID.fromString(new String(members.get(i), StandardCharsets.UTF_8));
                values.add(this.redis.get(incoming ? requestKey(other, player) : requestKey(player, other)).toCompletableFuture());
            }
            return CompletableFuture.allOf(values.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                List<TpaRequest> requests = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (int i = 0, size = values.size(); i < size; i++) {
                    byte[] value = values.get(i).join();
                    if (value == null) continue;
                    TpaRequest request = TpaRequest.decode(value);
                    if (request.expiresAt() > now) requests.add(request);
                }
                requests.sort(Comparator.comparingLong(TpaRequest::createdAt).reversed());
                return requests;
            });
        });
    }

    private static byte[] requestKey(UUID from, UUID to) {
        return bytes(PREFIX + "request:" + from + ":" + to);
    }

    private static byte[] incomingKey(UUID player) {
        return bytes(PREFIX + "in:" + player);
    }

    private static byte[] outgoingKey(UUID player) {
        return bytes(PREFIX + "out:" + player);
    }

    private static byte[] deniedKey(UUID from, UUID to) {
        return bytes(PREFIX + "denied:" + from + ":" + to);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    record CreateResult(int status, long remainingMillis) {
    }
}
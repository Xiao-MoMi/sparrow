package net.momirealms.sparrow.feature.tpa;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TpaStore {
    private static final String INCOMING_PREFIX = "sparrow:tpa:in:";       // 后接目标 UUID, 哈希的字段是发起者 UUID
    private static final String OUTGOING_PREFIX = "sparrow:tpa:out:";      // 后接发起者 UUID, 哈希的字段是目标 UUID
    private static final String COOLDOWN_PREFIX = "sparrow:tpa:cooldown:"; // 后接发起者 UUID, 过期即可以再次发起
    private static final String DENIED_PREFIX = "sparrow:tpa:denied:";     // 后接发起者与目标的 UUID, 过期即可以再次向他发起
    private static final long GRACE_MILLIS = 10_000L;                      // 记录比请求多保留的时间, 请求正常由过期任务删除
    private static final byte[] MARK = {1};                                // 两种冷却只看键在不在, 值没有含义

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final TpaFeature feature = this.plugin.featureManager().feature(TpaFeature.ID, TpaFeature.class);

    // 把请求记进目标的收到列表和发起者的发出列表
    @NotNull
    CompletableFuture<Void> save(@NotNull TpaRequest request) {
        RedisAsyncCommands<byte[], byte[]> redis = this.redis();
        byte[] incoming = key(INCOMING_PREFIX, request.target());
        byte[] outgoing = key(OUTGOING_PREFIX, request.requester());
        byte[] value = request.encode();
        // 两个键的过期时间随每次写入顺延, 只在没有人来删的时候起作用
        long ttl = this.expireMillis() + GRACE_MILLIS;
        return CompletableFuture.allOf(
                redis.hset(incoming, field(request.requester()), value).toCompletableFuture(),
                redis.pexpire(incoming, ttl).toCompletableFuture(),
                redis.hset(outgoing, field(request.target()), value).toCompletableFuture(),
                redis.pexpire(outgoing, ttl).toCompletableFuture()
        );
    }

    // 结果为这次调用是否真的删掉了请求. 接受、拒绝、取消和过期同时发生时, 只有删成功的一方生效
    @NotNull
    CompletableFuture<Boolean> take(@NotNull TpaRequest request) {
        RedisAsyncCommands<byte[], byte[]> redis = this.redis();
        return redis.hdel(key(INCOMING_PREFIX, request.target()), field(request.requester()))
                .toCompletableFuture()
                .thenApply(removed -> {
                    if (removed == 0) return false;
                    // 谁删成功由收到列表决定, 发出列表跟着清掉这一项
                    redis.hdel(key(OUTGOING_PREFIX, request.requester()), field(request.target()));
                    return true;
                });
    }

    // 查询发起者发给目标的那一条请求, 没有或已经超过有效期时为 null
    @NotNull
    CompletableFuture<@Nullable TpaRequest> find(@NotNull UUID requester, @NotNull UUID target) {
        return this.redis().hget(key(INCOMING_PREFIX, target), field(requester))
                .toCompletableFuture()
                .thenApply(value -> {
                    if (value == null) return null;
                    TpaRequest request = TpaRequest.decode(requester, target, value);
                    return this.expired(request) ? null : request;
                });
    }

    // 目标收到的、还在有效期内的全部请求
    @NotNull
    CompletableFuture<List<TpaRequest>> incoming(@NotNull UUID target) {
        return this.redis().hgetall(key(INCOMING_PREFIX, target))
                .toCompletableFuture()
                .thenApply(entries -> this.pending(entries, (requester, value) -> TpaRequest.decode(requester, target, value)));
    }

    // 发起者发出的、还在有效期内的全部请求
    @NotNull
    CompletableFuture<List<TpaRequest>> outgoing(@NotNull UUID requester) {
        return this.redis().hgetall(key(OUTGOING_PREFIX, requester))
                .toCompletableFuture()
                .thenApply(entries -> this.pending(entries, (target, value) -> TpaRequest.decode(requester, target, value)));
    }

    // 占用发起冷却. 结果为 0 时占用成功, 否则是还要等待的毫秒数
    @NotNull
    CompletableFuture<Long> claimCooldown(@NotNull UUID requester) {
        long millis = this.feature.config().requestCooldownSeconds() * 1000L;
        if (millis <= 0) return CompletableFuture.completedFuture(0L);
        RedisAsyncCommands<byte[], byte[]> redis = this.redis();
        byte[] key = key(COOLDOWN_PREFIX, requester);
        // 键不存在时写入成功就是占用成功, 已经存在时读出它的剩余时间, 至少算 1 毫秒
        return redis.set(key, MARK, SetArgs.Builder.nx().px(millis))
                .toCompletableFuture()
                .thenCompose(claimed -> claimed != null ? CompletableFuture.completedFuture(0L) : redis.pttl(key).toCompletableFuture().thenApply(left -> Math.max(left, 1L)));
    }

    // 目标拒绝发起者后调用, 开始这两人之间的单人冷却
    void markDenied(@NotNull UUID requester, @NotNull UUID target) {
        long millis = this.feature.config().denyCooldownSeconds() * 1000L;
        if (millis > 0) this.redis().set(deniedKey(requester, target), MARK, SetArgs.Builder.px(millis));
    }

    // 被目标拒绝后还要等待的毫秒数, 没有被拒绝过时为 0
    @NotNull
    CompletableFuture<Long> deniedMillis(@NotNull UUID requester, @NotNull UUID target) {
        return this.redis().pttl(deniedKey(requester, target)).toCompletableFuture().thenApply(left -> Math.max(left, 0L));
    }

    // 把哈希里的各项读成请求, 跳过已经超过有效期的
    private List<TpaRequest> pending(Map<byte[], byte[]> entries, Decoder decoder) {
        List<TpaRequest> requests = new ArrayList<>(entries.size());
        for (Map.Entry<byte[], byte[]> entry : entries.entrySet()) {
            TpaRequest request = decoder.decode(UUID.fromString(new String(entry.getKey(), StandardCharsets.UTF_8)), entry.getValue());
            if (!this.expired(request)) requests.add(request);
        }
        return requests;
    }

    // 请求到期时由过期任务删除. 安排任务的服务器停掉后它会留在哈希里, 超过有效期加宽限时间就当作不存在
    private boolean expired(TpaRequest request) {
        return request.createdAt() + this.expireMillis() + GRACE_MILLIS < System.currentTimeMillis();
    }

    private long expireMillis() {
        return this.feature.config().expireSeconds() * 1000L;
    }

    private RedisAsyncCommands<byte[], byte[]> redis() {
        return this.plugin.redisConnector().connection().async();
    }

    private static byte[] key(String prefix, UUID player) {
        return (prefix + player).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] deniedKey(UUID requester, UUID target) {
        return (DENIED_PREFIX + requester + ":" + target).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] field(UUID player) {
        return player.toString().getBytes(StandardCharsets.UTF_8);
    }

    // 哈希的字段是另一位玩家的 UUID, 它是发起者还是目标由调用方决定
    @FunctionalInterface
    private interface Decoder {
        TpaRequest decode(UUID other, byte[] value);
    }
}
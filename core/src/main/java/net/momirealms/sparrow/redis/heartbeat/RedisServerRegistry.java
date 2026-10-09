package net.momirealms.sparrow.redis.heartbeat;

import io.lettuce.core.KeyValue;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import net.momirealms.sparrow.cluster.ServerStatus;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.GsonHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class RedisServerRegistry {
    private static final long TTL_MILLIS = 4000; // 身份与状态的存活时间, 每次成功续期重新计时
    private static final byte[] OCCUPIED = bytes("1");
    private static final String IDENTITY_PREFIX = "sparrow:server:"; // 启动时按 server-id 排重的占用标记
    private static final String STATUS_PREFIX = "sparrow:server-status:"; // 值为整份 ServerStatus JSON
    private static final byte[] KNOWN_SERVERS_KEY = bytes("sparrow:known-servers"); // 离线 ID 保留, 在线状态由记录键决定.

    /**
     * 在启动时原子检查并占用服务器 ID, 占用标记存活 4 秒.
     *
     * @return 登记成功时为 true, 同名标记已经存在时为 false
     */
    @NotNull
    public CompletableFuture<Boolean> claim(@NotNull String serverId) {
        return this.commands().set(bytes(IDENTITY_PREFIX + serverId), OCCUPIED, SetArgs.Builder.nx().px(TTL_MILLIS))
                .toCompletableFuture()
                .thenApply("OK"::equals);
    }

    /**
     * 为已经登记的服务器更新占用标记及 TTL, 状态发布由 {@link #write(ServerStatus)} 完成.
     */
    @NotNull
    public CompletableFuture<Void> renewIdentity(@NotNull String serverId) {
        return this.commands().set(bytes(IDENTITY_PREFIX + serverId), OCCUPIED, SetArgs.Builder.px(TTL_MILLIS))
                .toCompletableFuture()
                .thenApply(ignored -> null);
    }

    /**
     * 更新本服占用标记、整份状态与已知服务器索引.
     *
     * @return 本轮所有写入完成的任务, Redis 失败时异常完成
     */
    @NotNull
    public CompletableFuture<Void> write(@NotNull ServerStatus status) {
        RedisAsyncCommands<byte[], byte[]> commands = this.commands();
        // 在返回前提交本轮全部命令, 心跳发布者可以按同一连接的提交顺序安排后续注销.
        CompletableFuture<String> identity = commands.set(bytes(IDENTITY_PREFIX + status.serverId()), OCCUPIED, SetArgs.Builder.px(TTL_MILLIS)).toCompletableFuture();
        CompletableFuture<String> state = commands.set(bytes(STATUS_PREFIX + status.serverId()), bytes(GsonHelper.get().toJson(status)), SetArgs.Builder.px(TTL_MILLIS)).toCompletableFuture();
        CompletableFuture<Long> known = commands.sadd(KNOWN_SERVERS_KEY, bytes(status.serverId())).toCompletableFuture();
        return CompletableFuture.allOf(identity, state, known);
    }

    /**
     * 删除本服的占用标记和状态记录, 已知服务器集合继续保留该 ID.
     */
    @NotNull
    public CompletableFuture<Void> remove(@NotNull String serverId) {
        return this.commands().del(bytes(IDENTITY_PREFIX + serverId), bytes(STATUS_PREFIX + serverId))
                .toCompletableFuture()
                .thenApply(ignored -> null);
    }

    /**
     * 读取 Redis 当前保存的服务器状态, 记录已过期或已注销时返回 null.
     */
    @NotNull
    public CompletableFuture<@Nullable ServerStatus> read(@NotNull String serverId) {
        return this.commands().get(bytes(STATUS_PREFIX + serverId))
                .toCompletableFuture()
                .thenApply(value -> value == null ? null : decode(value));
    }

    /**
     * 按已知服务器索引批量读取现存状态, 返回以服务器 ID 为键的映射.
     */
    @NotNull
    public CompletableFuture<Map<String, ServerStatus>> readAll() {
        RedisAsyncCommands<byte[], byte[]> commands = this.commands();
        return commands.smembers(KNOWN_SERVERS_KEY)
                .toCompletableFuture()
                .thenCompose(servers -> {
                    if (servers.isEmpty()) return CompletableFuture.completedFuture(Map.of());
                    // 索引与状态分两次读取, 此后新登记的服务器由变更通知或下一轮刷新补入.
                    byte[][] keys = new byte[servers.size()][];
                    int index = 0;
                    for (byte[] server : servers) {
                        keys[index++] = bytes(STATUS_PREFIX + new String(server, StandardCharsets.UTF_8));
                    }
                    return commands.mget(keys)
                            .toCompletableFuture()
                            .thenApply(values -> {
                                Map<String, ServerStatus> statuses = new HashMap<>();
                                for (int i = 0, size = values.size(); i < size; i++) {
                                    KeyValue<byte[], byte[]> value = values.get(i);
                                    // 已知 ID 可以长期保留, 只有本次仍读到记录的服务器进入结果.
                                    if (!value.hasValue()) continue;
                                    ServerStatus status = decode(value.getValue());
                                    statuses.put(status.serverId(), status);
                                }
                                return statuses;
                            });
                });
    }

    private RedisAsyncCommands<byte[], byte[]> commands() {
        return SparrowPlugin.instance().redisConnector().connection().async();
    }

    private static ServerStatus decode(byte[] value) {
        return GsonHelper.get().fromJson(new String(value, StandardCharsets.UTF_8), ServerStatus.class);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
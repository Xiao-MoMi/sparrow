package net.momirealms.sparrow.server;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.server.message.ServerChangedMessage;
import net.momirealms.sparrow.server.message.ServerHeartbeatMessage;
import net.momirealms.sparrow.util.LazyReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public final class ServerRegistry {
    private static final long IDENTITY_TTL_MILLIS = 5000; // ID 占用的有效期
    private static final byte[] OCCUPIED = new byte[]{1};
    private static final String IDENTITY_PREFIX = "sparrow:server:identity:";
    private static final byte[] SNAPSHOTS_KEY = "sparrow:server:snapshots".getBytes(StandardCharsets.UTF_8); // 完整快照无过期时间, 供初始化读取

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final String serverId = ServerConfig.serverId();
    private final LazyReference<RedisAsyncCommands<byte[], byte[]>> commands = LazyReference.oneTime(() -> this.plugin.redisConnector().connection().async());

    private @Nullable ServerStatus lastPublishedStatus; // 最近一次提交发布的状态, null 表示尚未提交启动通知
    private boolean claimed;
    private boolean closing; // 开始注销后停止本服发布

    private final Map<String, ServerStatus> servers = new ConcurrentHashMap<>();
    private final Cache<String, Boolean> alive = Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();
    private @Nullable List<RedisMessage<FriendlyByteBuf>> loading;
    private boolean closed; // 注销完成后停止接收并清空缓存

    void onLoad() {
        String result = this.commands.get()
                .set(identityKey(this.serverId), OCCUPIED, SetArgs.Builder.nx().px(IDENTITY_TTL_MILLIS))
                .toCompletableFuture()
                .join();
        if (!"OK".equals(result)) {
            this.plugin.logger().error(LogConstants.SERVER_ID_DUPLICATE, this.serverId);
            throw new IllegalStateException("Server ID " + this.serverId + " is already registered in Redis");
        }
        this.claimed = true;
    }

    // 用 Redis 完整快照重建数据, 并按顺序合并读取期间收到的消息.
    @NotNull
    synchronized CompletableFuture<Void> refreshFromRedis() {
        if (this.closed) return CompletableFuture.completedFuture(null);
        List<RedisMessage<FriendlyByteBuf>> changes = new ArrayList<>();
        this.loading = changes;
        // 读取全部
        return this.commands.get()
                .hgetall(SNAPSHOTS_KEY)
                .toCompletableFuture()
                .thenApply(fields -> {
                    Map<String, ServerStatus> statuses = new HashMap<>();
                    fields.forEach((key, value) -> {
                        String serverId = new String(key, StandardCharsets.UTF_8);
                        ByteBuf buffer = Unpooled.wrappedBuffer(value);
                        try {
                            Tag tag = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer).readNbt(false);
                            if (!(tag instanceof CompoundTag data))
                                throw new DecoderException("Server snapshot must be a compound tag");
                            statuses.put(serverId, ServerStatus.fromNbt(serverId, data));
                        } finally {
                            buffer.release();
                        }
                    });
                    return statuses;
                })
                .thenAccept(snapshot -> {
                    synchronized (this) {
                        if (this.closed) return;
                        this.loading = null;
                        // Redis 快照提供完整状态的合并起点.
                        this.servers.clear();
                        this.servers.putAll(snapshot);
                        // 按接收顺序重放增删和增量, 存活标记沿用接收时的有效期.
                        for (int i = 0, size = changes.size(); i < size; i++) {
                            RedisMessage<FriendlyByteBuf> message = changes.get(i);
                            if (message instanceof ServerChangedMessage changed) {
                                this.applyChanged(changed);
                            } else if (message instanceof ServerHeartbeatMessage heartbeat) {
                                this.applyChanges(heartbeat);
                            }
                        }
                    }
                });
    }

    // 首次采集前 status 为 null, 此时维持身份占用.
    void heartbeat(@Nullable ServerStatus status) {
        CompletableFuture<Void> operation;
        synchronized (this) {
            if (this.closing) return;
            CompletableFuture<Void> renewal = this.renewIdentity();
            if (status == null) {
                operation = renewal;
            } else {
                CompletableFuture<Void> publication = this.lastPublishedStatus == null ? this.publishInitial(status) : this.publishChanges(status);
                operation = CompletableFuture.allOf(renewal, publication);
            }
        }
        operation.whenComplete((ignored, error) -> {
            if (error != null) {
                this.plugin.logger().warn("Failed to publish server state for " + this.serverId, error);
            }
        });
    }

    @NotNull
    private CompletableFuture<Void> renewIdentity() {
        return this.commands.get().set(identityKey(this.serverId), OCCUPIED, SetArgs.Builder.px(IDENTITY_TTL_MILLIS))
                .toCompletableFuture()
                .thenApply(ignored -> null);
    }

    // 调用方持有注册表锁, 快照写入和启动通知通过同一 Redis 连接按序提交.
    @NotNull
    private CompletableFuture<Void> publishInitial(@NotNull ServerStatus status) {
        ServerChangedMessage message = new ServerChangedMessage(this.serverId, status.toNbt());
        CompletableFuture<Void> write = this.writeSnapshot(status);
        this.updateServer(message);
        CompletableFuture<Long> publication = this.plugin.messageBrokerManager().publishOneWay(message, "");
        this.lastPublishedStatus = status;
        return CompletableFuture.allOf(write, publication);
    }

    // 调用方持有注册表锁, lastPublishedStatus 已有启动状态.
    @NotNull
    private CompletableFuture<Void> publishChanges(@NotNull ServerStatus status) {
        // 以上次提交的状态计算增量, 字段变化时保存新的 Redis 完整快照.
        CompoundTag changes = status.diff(this.lastPublishedStatus);
        CompletableFuture<Void> write = CompletableFuture.completedFuture(null);
        if (!changes.isEmpty()) {
            write = this.writeSnapshot(status);
        }
        // 空增量仍作为心跳发布, 供各服续期存活标记.
        ServerHeartbeatMessage message = new ServerHeartbeatMessage(this.serverId, changes);
        this.applyHeartbeat(message);
        CompletableFuture<Long> publication = this.plugin.messageBrokerManager().publishOneWay(message, "");
        this.lastPublishedStatus = status;
        return CompletableFuture.allOf(write, publication);
    }

    // 保存完整快照, 服务器 ID 作为 Hash 字段, 状态以 NBT 字节存储.
    @NotNull
    private CompletableFuture<Void> writeSnapshot(@NotNull ServerStatus status) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            new net.momirealms.sparrow.util.FriendlyByteBuf(buffer).writeNbt(status.toNbt(), false);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return this.commands.get().hset(SNAPSHOTS_KEY, status.serverId().getBytes(StandardCharsets.UTF_8), bytes)
                    .toCompletableFuture()
                    .thenApply(ignored -> null);
        } finally {
            buffer.release();
        }
    }

    // 启动通知刷新存活标记, 并用完整状态替换同名记录.
    public synchronized void updateServer(@NotNull ServerChangedMessage message) {
        if (this.closed) return;
        this.alive.put(message.serverId(), true);
        if (this.loading != null) {
            this.loading.add(message);
            return;
        }
        this.applyChanged(message);
    }

    // 关闭通知立即撤销存活标记, 并删除该服的保留状态.
    public synchronized void removeServer(@NotNull ServerChangedMessage message) {
        if (this.closed) return;
        this.alive.invalidate(message.serverId());
        if (this.loading != null) {
            this.loading.add(message);
            return;
        }
        this.applyChanged(message);
    }

    // 按接收时间续期存活标记, 并把增量合并到已有完整状态.
    public synchronized void applyHeartbeat(@NotNull ServerHeartbeatMessage message) {
        if (this.closed) return;
        // 空增量也是有效心跳.
        this.alive.put(message.serverId(), true);
        if (this.loading != null) {
            this.loading.add(message);
            return;
        }
        this.applyChanges(message);
    }

    private void applyChanged(@NotNull ServerChangedMessage message) {
        if (message.snapshot() == null) {
            this.servers.remove(message.serverId());
        } else {
            this.servers.put(message.serverId(), ServerStatus.fromNbt(message.serverId(), message.snapshot()));
        }
    }

    private void applyChanges(@NotNull ServerHeartbeatMessage message) {
        ServerStatus current = this.servers.get(message.serverId());
        if (current == null) return;
        if (message.changes().isEmpty()) return;
        this.servers.put(message.serverId(), current.applyChanges(message.changes()));
    }

    @Nullable
    ServerStatus getServer(@NotNull String serverId) {
        return this.servers.get(serverId);
    }

    @NotNull
    List<ServerStatus> getServers() {
        return this.servers.values().stream()
                .sorted(Comparator.comparing(ServerStatus::serverId))
                .toList();
    }

    @Nullable
    ServerStatus getOnlineServer(@NotNull String serverId) {
        return this.alive.getIfPresent(serverId) == null ? null : this.servers.get(serverId);
    }

    @NotNull
    List<ServerStatus> getOnlineServers() {
        return this.servers.values().stream()
                .filter(status -> this.alive.getIfPresent(status.serverId()) != null)
                .sorted(Comparator.comparing(ServerStatus::serverId))
                .toList();
    }

    @NotNull
    List<ServerStatus> getOnlineServersByTag(@NotNull String tag) {
        return this.getOnlineServers().stream()
                .filter(status -> status.tags().contains(tag))
                .toList();
    }

    boolean isOnline(@NotNull String serverId) {
        return this.getOnlineServer(serverId) != null;
    }

    synchronized boolean closing() {
        return this.closing;
    }

    @NotNull
    synchronized CompletableFuture<Void> shutdown() {
        if (this.closing) return CompletableFuture.completedFuture(null);
        this.closing = true;
        // 如果没有占领过心跳则直接清理缓存.
        if (!this.claimed) {
            this.clearCache();
            return CompletableFuture.completedFuture(null);
        }
        // 删除 redis 快照.
        CompletableFuture<Void> removal = this.commands.get()
                .hdel(SNAPSHOTS_KEY, this.serverId.getBytes(StandardCharsets.UTF_8))
                .toCompletableFuture()
                .thenApply(ignored -> null);
        // 如果发布过状态则代表自己在其他服务器上过线, 需要广播下线.
        if (this.lastPublishedStatus != null) {
            ServerChangedMessage message = new ServerChangedMessage(this.serverId);
            this.removeServer(message);
            CompletableFuture<Long> publication = this.plugin.messageBrokerManager().publishOneWay(message, "");
            removal = CompletableFuture.allOf(removal, publication);
        }
        // 删除心跳 key.
        CompletableFuture<Object> releaseFuture = this.commands.get()
                .del(identityKey(this.serverId))
                .toCompletableFuture()
                .thenApply(ignored -> null);
        return CompletableFuture.allOf(removal, releaseFuture)
                .whenComplete((ignored, error) -> {
                    synchronized (this) {
                        this.clearCache();
                    }
                });
    }

    private void clearCache() {
        this.closed = true;
        this.loading = null;
        this.alive.invalidateAll();
        this.servers.clear();
    }

    private static byte[] identityKey(@NotNull String serverId) {
        return (IDENTITY_PREFIX + serverId).getBytes(StandardCharsets.UTF_8);
    }
}
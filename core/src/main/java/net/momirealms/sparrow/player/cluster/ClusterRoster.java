package net.momirealms.sparrow.player.cluster;

import io.lettuce.core.RedisException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.util.UUIDUtils;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * 集群内各服务器的在线玩家名单. 进退服通知会立即更新名单, 每 30 秒还会按心跳存活的服务器全量校准一次, 结果可能短暂落后于实际状态.
 */
public final class ClusterRoster {
    private static final String ROSTER_PREFIX = "sparrow:online-players:"; // Hash 结构, UUID 字节 -> 玩家名
    private static final long REFRESH_MILLIS = 30000;
    private static final long ROSTER_TTL_MILLIS = REFRESH_MILLIS * 3;      // 本服停止校准后名单自行过期
    private static final String REWRITE_SCRIPT = "redis.call('DEL', KEYS[1]) if #ARGV > 1 then redis.call('HSET', KEYS[1], unpack(ARGV, 2)) end redis.call('PEXPIRE', KEYS[1], ARGV[1]) return 1";

    private final SparrowPlugin plugin;
    private final PlayerManager players;
    // 以下状态由当前对象的做锁保护
    private final Map<String, Map<UUID, ClusterPlayer>> servers = new HashMap<>();
    private @Nullable Set<String> refreshChanges; // 非 null 表示校准在途, 记录期间收到通知的服务器
    private volatile OnlineView view = OnlineView.EMPTY;
    private volatile boolean closed;
    private String serverId;
    private byte[] rosterKey;
    private SchedulerTask task;

    @ApiStatus.Internal
    public ClusterRoster(@NotNull SparrowPlugin plugin, @NotNull PlayerManager players) {
        this.plugin = plugin;
        this.players = players;
    }

    @ApiStatus.Internal
    public void onEnable() {
        this.serverId = ServerConfig.serverId();
        this.rosterKey = rosterKey(this.serverId);
        PlayerPresenceMessage.listener(this::accept);
        this.task = this.plugin.scheduler().asyncRepeating(this::refresh, 0, REFRESH_MILLIS, TimeUnit.MILLISECONDS);
    }

    // 本地视图立即更新, 名单写入和通知走同一条 Redis 连接, 其他服收到通知时名单已经写好.
    @ApiStatus.Internal
    public synchronized void presence(@NotNull UUID uuid, @NotNull String name, boolean joined) {
        if (this.closed) return;
        PlayerPresenceMessage message = new PlayerPresenceMessage(this.serverId, uuid, name, joined);
        this.accept(message);
        RedisAsyncCommands<byte[], byte[]> commands = this.plugin.redisConnector().connection().async();
        if (joined) {
            commands.hset(this.rosterKey, UUIDUtils.toBytes(uuid), name.getBytes(StandardCharsets.UTF_8));
            commands.pexpire(this.rosterKey, ROSTER_TTL_MILLIS);
        } else {
            commands.hdel(this.rosterKey, UUIDUtils.toBytes(uuid));
        }
        MessageBroker<ByteBuf> broker = this.plugin.messageBrokerManager().broker();
        commands.publish(broker.channel(), broker.encode(message));
    }

    private void refresh() {
        synchronized (this) {
            if (this.closed || this.refreshChanges != null) return;
            this.refreshChanges = new HashSet<>();
            // 与进退服写入共用监视器, 本服名单的全量重写不会覆盖更晚的增量
            this.rewriteLocalRoster();
        }
        this.readRosters().whenComplete((rosters, failure) -> {
            synchronized (this) {
                if (failure == null && !this.closed) {
                    // 校准期间收到通知的服务器以本地视图为准, 其余采用本轮快照
                    for (Map.Entry<String, Map<UUID, ClusterPlayer>> entry : rosters.entrySet()) {
                        if (!this.refreshChanges.contains(entry.getKey())) {
                            this.servers.put(entry.getKey(), entry.getValue());
                        }
                    }
                    this.servers.keySet().removeIf(id -> !rosters.containsKey(id) && !this.refreshChanges.contains(id));
                    this.rebuildView();
                }
                this.refreshChanges = null;
            }
            // Redis 暂时不可用时保留本地视图, 下一轮校准补齐
            Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
            if (cause != null && !(cause instanceof RedisException)) {
                this.plugin.logger().warn(TranslationManager.console(LogConstants.PLAYER_ROSTER_REFRESH_FAILED), cause);
            }
        });
    }

    // 脚本内删除后重建, 其他服读取时不会看到清空后的空名单.
    private void rewriteLocalRoster() {
        Collection<SparrowPlayer> online = this.players.getOnlinePlayers();
        byte[][] arguments = new byte[1 + online.size() * 2][];
        arguments[0] = Long.toString(ROSTER_TTL_MILLIS).getBytes(StandardCharsets.UTF_8);
        int index = 1;
        for (SparrowPlayer player : online) {
            arguments[index++] = UUIDUtils.toBytes(player.uniqueId());
            arguments[index++] = player.name().getBytes(StandardCharsets.UTF_8);
        }
        this.plugin.redisConnector().connection().async().eval(REWRITE_SCRIPT, ScriptOutputType.INTEGER, new byte[][]{this.rosterKey}, arguments);
    }

    // 只读取心跳仍存活的服务器, 崩溃服务器残留的名单在过期前也会被忽略.
    private CompletableFuture<Map<String, Map<UUID, ClusterPlayer>>> readRosters() {
        RedisAsyncCommands<byte[], byte[]> commands = this.plugin.redisConnector().connection().async();
        return this.plugin.serverHeartBeats().onlineServers().thenCompose(servers -> {
            // 各服名单并发读取, 全部返回后再组装
            Map<String, CompletableFuture<Map<byte[], byte[]>>> pending = new HashMap<>();
            for (String server : servers) {
                pending.put(server, commands.hgetall(rosterKey(server)).toCompletableFuture());
            }
            return CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                Map<String, Map<UUID, ClusterPlayer>> rosters = new HashMap<>();
                pending.forEach((server, fields) -> {
                    Map<UUID, ClusterPlayer> roster = new HashMap<>();
                    fields.join().forEach((uuid, name) -> {
                        UUID playerId = UUIDUtils.fromBytes(uuid);
                        roster.put(playerId, new ClusterPlayer(playerId, new String(name, StandardCharsets.UTF_8), server));
                    });
                    if (!roster.isEmpty()) rosters.put(server, roster);
                });
                return rosters;
            });
        });
    }

    private synchronized void accept(PlayerPresenceMessage message) {
        if (this.closed) return;
        if (this.refreshChanges != null) {
            this.refreshChanges.add(message.serverId());
        }
        Map<UUID, ClusterPlayer> roster = this.servers.computeIfAbsent(message.serverId(), ignored -> new HashMap<>());
        if (message.joined()) {
            ClusterPlayer player = new ClusterPlayer(message.uuid(), message.name(), message.serverId());
            // 本服广播回环和重复通知不重建视图
            if (player.equals(roster.put(player.uuid(), player))) return;
        } else {
            // 跨服时旧服的离线通知只移除旧服记录
            if (roster.remove(message.uuid()) == null) return;
            if (roster.isEmpty()) {
                this.servers.remove(message.serverId());
            }
        }
        this.rebuildView();
    }

    private void rebuildView() {
        Map<UUID, ClusterPlayer> merged = new HashMap<>();
        for (Map<UUID, ClusterPlayer> roster : this.servers.values()) {
            merged.putAll(roster);
        }
        this.view = OnlineView.of(merged);
    }

    /**
     * 返回集群在线玩家, 按名字忽略大小写排序.
     *
     * @return 最近一次名单变化时的只读快照
     */
    @NotNull
    public List<ClusterPlayer> players() {
        return this.view.players();
    }

    /**
     * 按名字查找集群在线玩家, 名字忽略大小写.
     *
     * @param name 玩家名
     * @return 在线玩家及其所在服务器, 不在线时为 null
     */
    @Nullable
    public ClusterPlayer find(@NotNull String name) {
        return this.view.byName().get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * 按 UUID 查找集群在线玩家.
     *
     * @param uuid 玩家 UUID
     * @return 在线玩家及其所在服务器, 不在线时为 null
     */
    @Nullable
    public ClusterPlayer find(@NotNull UUID uuid) {
        return this.view.byUuid().get(uuid);
    }

    /**
     * 按前缀返回集群在线玩家名的命令补全项, 前缀忽略大小写, 空前缀返回全部.
     *
     * @param prefix 已输入的名字前缀
     * @return 按名字排序的补全项
     */
    @NotNull
    public List<Suggestion> suggest(@NotNull String prefix) {
        OnlineView current = this.view;
        if (prefix.isEmpty()) return current.suggestions();
        List<Suggestion> result = new ArrayList<>();
        List<Suggestion> candidates = current.suggestions();
        int size = candidates.size();
        for (int i = 0; i < size; i++) {
            Suggestion candidate = candidates.get(i);
            if (candidate.suggestion().regionMatches(true, 0, prefix, 0, prefix.length())) {
                result.add(candidate);
            }
        }
        return result;
    }

    // 需要在 Redis 连接关闭前调用, 删除命令排在本服已发出的名单写入之后.
    @ApiStatus.Internal
    public void shutdown() {
        synchronized (this) {
            PlayerPresenceMessage.listener(null);
            this.closed = true;
            this.servers.clear();
            this.view = OnlineView.EMPTY;
            if (this.task == null) return;
            this.task.cancel();
        }
        this.plugin.redisConnector().connection().async().del(this.rosterKey);
    }

    private static byte[] rosterKey(String serverId) {
        return (ROSTER_PREFIX + serverId).getBytes(StandardCharsets.UTF_8);
    }

    private record OnlineView(List<ClusterPlayer> players, Map<String, ClusterPlayer> byName, Map<UUID, ClusterPlayer> byUuid, List<Suggestion> suggestions) {
        private static final OnlineView EMPTY = new OnlineView(List.of(), Map.of(), Map.of(), List.of());

        // 排序在名单变化时完成, 补全直接复用同一份顺序和 Suggestion 对象
        private static OnlineView of(Map<UUID, ClusterPlayer> byUuid) {
            List<ClusterPlayer> players = byUuid.values().stream().sorted(Comparator.comparing(ClusterPlayer::name, String.CASE_INSENSITIVE_ORDER)).toList();
            Map<String, ClusterPlayer> byName = new HashMap<>();
            List<Suggestion> suggestions = new ArrayList<>(players.size());
            int size = players.size();
            for (int i = 0; i < size; i++) {
                ClusterPlayer player = players.get(i);
                byName.put(player.name().toLowerCase(Locale.ROOT), player);
                suggestions.add(Suggestion.suggestion(player.name()));
            }
            return new OnlineView(players, byName, byUuid, suggestions);
        }
    }
}

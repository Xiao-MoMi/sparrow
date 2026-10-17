package net.momirealms.sparrow.player;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.player.message.proxy.PlayerPresenceMessage;
import net.momirealms.sparrow.util.UUIDUtils;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerCache {
    private static final byte[] ROSTER_KEY = "sparrow:proxy:players".getBytes(StandardCharsets.UTF_8);

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Map<UUID, OnlineEntry> players = new HashMap<>(); // 在缓存锁内合并的代理在线记录
    private volatile OnlineView view = OnlineView.EMPTY; // 查询线程读取的整轮名单和索引
    private @Nullable List<PlayerPresenceMessage> loading; // 暂存全量读取期间的消息, 列表实例同时标识读取轮次
    private boolean closed;

    // 从 Redis 全量刷新在线名单, 合并读取期间的增量后发布, 连续刷新以最后一轮为准.
    public synchronized void refreshFromRedis() {
        if (this.closed) return;
        // 拉取中接收到的修改
        List<PlayerPresenceMessage> changes = new ArrayList<>();
        this.loading = changes;
        // 拉取 Redis 的数据
        CompletableFuture<Map<UUID, PlayerPresenceMessage>> redisOnlinePlayers = this.plugin.redisConnector().connection().async().hgetall(ROSTER_KEY)
                .thenApply(fields -> {
                    Map<UUID, PlayerPresenceMessage> roster = new HashMap<>();
                    fields.forEach((key, value) -> {
                        UUID uuid = UUIDUtils.fromBytes(key);
                        ByteBuf buffer = Unpooled.wrappedBuffer(value);
                        try {
                            roster.put(uuid, PlayerPresenceMessage.readOnlineRecord(uuid, buffer));
                        } finally {
                            buffer.release();
                        }
                    });
                    return roster;
                })
                .toCompletableFuture();
        // 更新本地
        redisOnlinePlayers.whenComplete((roster, error) -> {
            synchronized (this) {
                // 关闭或开始新一轮读取后, 当前回调结束处理.
                if (this.closed || this.loading != changes) return;
                this.loading = null;
                if (error != null) {
                    this.plugin.logger().warn("Failed to load proxy player directory", error);
                    return;
                }
                Map<UUID, OnlineEntry> entries = new HashMap<>();
                for (PlayerPresenceMessage message : roster.values()) {
                    entries.put(message.uuid(), OnlineEntry.of(message, this.players.get(message.uuid())));
                }
                this.players.clear();
                this.players.putAll(entries);
                // 按接收顺序应用读取期间的更新和删除.
                for (int i = 0, size = changes.size(); i < size; i++) {
                    PlayerPresenceMessage message = changes.get(i);
                    if (message.serverId() == null) {
                        this.players.remove(message.uuid());
                    } else {
                        this.players.put(message.uuid(), OnlineEntry.of(message, this.players.get(message.uuid())));
                    }
                }
                this.view = OnlineView.of(Map.copyOf(this.players));
            }
        });
    }

    // 应用玩家在线状态, 全量读取期间暂存原消息并在读取结束后合并.
    public synchronized void updatePlayer(@NotNull PlayerPresenceMessage message) {
        if (this.closed) return;
        if (this.loading != null) {
            this.loading.add(message);
            return;
        }
        this.players.put(message.uuid(), OnlineEntry.of(message, this.players.get(message.uuid())));
        this.view = OnlineView.of(Map.copyOf(this.players));
    }

    // 应用玩家离开集群的状态, 全量读取期间暂存原消息并在读取结束后合并.
    public synchronized void removePlayer(@NotNull PlayerPresenceMessage message) {
        if (this.closed) return;
        if (this.loading != null) {
            this.loading.add(message);
            return;
        }
        this.players.remove(message.uuid());
        this.view = OnlineView.of(Map.copyOf(this.players));
    }

    @NotNull
    public List<Suggestion> suggestGlobalPlayers(@NotNull String prefix) {
        return this.view.suggest(prefix);
    }

    @NotNull
    List<OfflineSparrowPlayer> globalOnlinePlayers() {
        return this.view.players();
    }

    @Nullable
    OfflineSparrowPlayer getGlobalOnlinePlayer(@NotNull String name) {
        return this.view.byName().get(name.toLowerCase(Locale.ROOT));
    }

    @Nullable
    OfflineSparrowPlayer getGlobalOnlinePlayer(@NotNull UUID uniqueId) {
        OnlineEntry entry = this.view.byUuid().get(uniqueId);
        return entry == null ? null : entry.player();
    }

    @Nullable
    String getCurrentServerId(@NotNull UUID uniqueId) {
        OnlineEntry entry = this.view.byUuid().get(uniqueId);
        return entry == null ? null : entry.serverId();
    }

    synchronized void shutdown() {
        this.closed = true;
        this.loading = null;
        this.players.clear();
        this.view = OnlineView.EMPTY;
    }

    private record OnlineEntry(@NotNull OfflineSparrowPlayer player, @NotNull String serverId) {

        private static OnlineEntry of(@NotNull PlayerPresenceMessage message, @Nullable OnlineEntry current) {
            OfflineSparrowPlayer player;
            if (current != null && current.player().name().equals(message.name()) && current.player().locale().equals(message.locale())) {
                player = current.player();
            } else {
                player = new OfflineSparrowPlayer(message.uuid(), message.name(), message.locale());
            }
            return new OnlineEntry(player, message.serverId());
        }
    }

    private record OnlineView(List<OfflineSparrowPlayer> players, Map<String, OfflineSparrowPlayer> byName, Map<UUID, OnlineEntry> byUuid, List<Suggestion> suggestions) {
        private static final OnlineView EMPTY = new OnlineView(List.of(), Map.of(), Map.of(), List.of()); // 首次名册发布前和管理器关闭后共享的空视图

        // 从同一批在线记录建立名字索引和排序候选.
        @NotNull
        private static OnlineView of(Map<UUID, OnlineEntry> byUuid) {
            // 名字顺序在视图创建时确定, 查询和补全共享这批记录.
            List<OfflineSparrowPlayer> players = byUuid.values()
                    .stream()
                    .map(OnlineEntry::player)
                    .sorted(Comparator.comparing(OfflineSparrowPlayer::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            // 名字索引使用小写键, 补全保留代理提供的名字拼写.
            Map<String, OfflineSparrowPlayer> byName = new HashMap<>();
            List<Suggestion> suggestions = new ArrayList<>(players.size());
            int size = players.size();
            for (int i = 0; i < size; i++) {
                OfflineSparrowPlayer player = players.get(i);
                byName.put(player.name().toLowerCase(Locale.ROOT), player);
                suggestions.add(Suggestion.suggestion(player.name()));
            }
            return new OnlineView(players, byName, byUuid, List.copyOf(suggestions));
        }

        @NotNull
        private List<Suggestion> suggest(String prefix) {
            if (prefix.isEmpty()) return this.suggestions;
            int low = 0;
            int high = this.suggestions.size();
            // 找到按忽略大小写顺序第一个不小于前缀的名字, 作为候选区间起点.
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (String.CASE_INSENSITIVE_ORDER.compare(this.suggestions.get(middle).suggestion(), prefix) < 0) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            int start = low;
            high = this.suggestions.size();
            // 前缀匹配项从起点连续排列, 上界指向最后一个匹配项之后的位置.
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (this.suggestions.get(middle).suggestion().regionMatches(true, 0, prefix, 0, prefix.length())) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return start == low ? List.of() : this.suggestions.subList(start, low);
        }
    }
}
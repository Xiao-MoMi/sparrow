package net.momirealms.sparrow.cluster;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.proxy.PlayerPresenceMessage;
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

public final class PlayerDirectory {
    private static final byte[] ROSTER_KEY = "sparrow:proxy:players".getBytes(StandardCharsets.UTF_8);

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Map<UUID, PlayerPresence> players = new HashMap<>();
    private volatile OnlineView view = OnlineView.EMPTY;
    private @Nullable List<PlayerPresenceMessage> loading;
    private boolean closed;

    public void onEnable() {
        this.plugin.messageBrokerManager().proxyBroker().subscribe();
        this.reload();
    }

    public synchronized void reload() {
        if (this.closed) return;
        List<PlayerPresenceMessage> changes = new ArrayList<>();
        this.loading = changes;
        this.readRoster().whenComplete((roster, error) -> {
            synchronized (this) {
                if (this.closed || this.loading != changes) return;
                this.loading = null;
                if (error != null) {
                    this.plugin.logger().warn("Failed to load proxy player directory", error);
                    return;
                }
                this.players.clear();
                this.players.putAll(roster);
                // 订阅先于全量读取, 读取期间的增量在快照之后按接收顺序应用.
                for (int i = 0, size = changes.size(); i < size; i++) {
                    this.apply(changes.get(i));
                }
                this.view = OnlineView.of(Map.copyOf(this.players));
            }
        });
    }

    private CompletableFuture<Map<UUID, PlayerPresence>> readRoster() {
        return this.plugin.redisConnector().connection().async().hgetall(ROSTER_KEY)
                .thenApply(fields -> {
                    Map<UUID, PlayerPresence> roster = new HashMap<>();
                    fields.forEach((key, value) -> {
                        UUID uuid = UUID.fromString(new String(key, StandardCharsets.UTF_8));
                        ByteBuf buffer = Unpooled.wrappedBuffer(value);
                        try {
                            roster.put(uuid, PlayerPresence.read(uuid, buffer));
                        } finally {
                            buffer.release();
                        }
                    });
                    return roster;
                })
                .toCompletableFuture();
    }

    public synchronized void accept(@NotNull PlayerPresenceMessage message) {
        if (this.closed) return;
        if (this.loading != null) {
            this.loading.add(message);
            return;
        }
        this.apply(message);
        this.view = OnlineView.of(Map.copyOf(this.players));
    }

    private void apply(PlayerPresenceMessage message) {
        if (message.player() == null) {
            this.players.remove(message.uuid());
        } else {
            this.players.put(message.uuid(), message.player());
        }
    }

    @NotNull
    public List<PlayerPresence> players() {
        return this.view.players();
    }

    @Nullable
    public PlayerPresence find(@NotNull String name) {
        return this.view.byName().get(name.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public PlayerPresence find(@NotNull UUID uuid) {
        return this.view.byUuid().get(uuid);
    }

    @NotNull
    public List<Suggestion> suggest(@NotNull String prefix) {
        return this.view.suggest(prefix);
    }

    public synchronized void shutdown() {
        this.closed = true;
        this.loading = null;
        this.players.clear();
        this.view = OnlineView.EMPTY;
    }

    private record OnlineView(
            List<PlayerPresence> players,
            Map<String, PlayerPresence> byName,
            Map<UUID, PlayerPresence> byUuid,
            List<Suggestion> suggestions
    ) {
        private static final OnlineView EMPTY = new OnlineView(List.of(), Map.of(), Map.of(), List.of());

        // 在排序名单中定位前缀范围, 返回共享 Suggestion 对象的只读子列表.
        private List<Suggestion> suggest(String prefix) {
            if (prefix.isEmpty()) return this.suggestions;
            int low = 0;
            int high = this.suggestions.size();
            // 找到第一个不小于前缀的名字, 包含所有大小写等价的重复名字.
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
            // 从起点往后的匹配项连续排列, 定位其后的第一个名字.
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

        // 排序在名单变化时完成, 补全直接复用同一份顺序和 Suggestion 对象
        private static OnlineView of(Map<UUID, PlayerPresence> byUuid) {
            List<PlayerPresence> players = byUuid.values()
                    .stream()
                    .sorted(Comparator.comparing(PlayerPresence::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            Map<String, PlayerPresence> byName = new HashMap<>();
            List<Suggestion> suggestions = new ArrayList<>(players.size());
            int size = players.size();
            for (int i = 0; i < size; i++) {
                PlayerPresence player = players.get(i);
                byName.put(player.name().toLowerCase(Locale.ROOT), player);
                suggestions.add(Suggestion.suggestion(player.name()));
            }
            return new OnlineView(players, byName, byUuid, List.copyOf(suggestions));
        }
    }
}
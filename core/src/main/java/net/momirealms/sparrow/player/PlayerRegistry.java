package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2IntHashTable;
import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.PlayerNameIndex.NameEntry;
import net.momirealms.sparrow.player.message.PlayerChangedMessage;
import net.momirealms.sparrow.player.message.PlayerRegistryResetMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.server.ServerManager;
import net.momirealms.sparrow.util.LazyReference;
import net.momirealms.sparrow.util.UUIDUtils;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class PlayerRegistry {
    private static final byte[] ONLINE_KEY = "sparrow:player:online".getBytes(StandardCharsets.UTF_8);
    // 参数与 UPDATE_SCRIPT 相同, 无条件覆盖记录
    private static final String LOGIN_SCRIPT = """
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
            redis.call('PUBLISH', ARGV[4], ARGV[5])
            return 1
            """;
    // 记录为空或属于本服时写入, 内容不变时不 pub 消息
    private static final String UPDATE_SCRIPT = """
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if current and string.sub(current, 1, #ARGV[2]) ~= ARGV[2] then
                return 0
            end
            if current == ARGV[3] then
                return 0
            end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
            redis.call('PUBLISH', ARGV[4], ARGV[5])
            return 1
            """;
    // 记录属于本服时删除
    private static final String QUIT_SCRIPT = """
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current or string.sub(current, 1, #ARGV[2]) ~= ARGV[2] then
                return 0
            end
            redis.call('HDEL', KEYS[1], ARGV[1])
            redis.call('PUBLISH', ARGV[3], ARGV[4])
            return 1
            """;
    private static final String RESET_SCRIPT = """
            local entries = redis.call('HGETALL', KEYS[1])
            for i = 1, #entries, 2 do
                if string.sub(entries[i + 1], 1, #ARGV[1]) == ARGV[1] then
                    redis.call('HDEL', KEYS[1], entries[i])
                end
            end
            redis.call('PUBLISH', ARGV[2], ARGV[3])
            return 1
            """;

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final ServerManager serverManager = this.plugin.serverManager();
    private final String serverId = ServerConfig.serverId();
    private final byte[] serverPrefix = PlayerChangedMessage.serverPrefix(this.serverId);
    private final LazyReference<RedisAsyncCommands<byte[], byte[]>> commands = LazyReference.oneTime(() -> this.plugin.redisConnector().connection().async());

    private final ConcurrentChainedObject2ObjectHashTable<UUID, OnlineEntry> byUuid = new ConcurrentChainedObject2ObjectHashTable<>();
    private final PlayerNameIndex byName = new PlayerNameIndex(); // 只存名字和 UUID, 读取方拿到的槽快照可能指向刚删除的记录
    private final ConcurrentChainedObject2IntHashTable<String> counts = new ConcurrentChainedObject2IntHashTable<>(); // 各服务器的记录数, 不含存活判断
    private @Nullable List<RedisMessage<FriendlyByteBuf>> loading = new ArrayList<>(); // 启动读取完成前暂存的消息, 读取完成后为 null
    private boolean closed;

    // 先删除本服遗留的记录再读取全部记录, 读取完成前收到的消息在装入后按顺序回放
    @NotNull
    CompletableFuture<Void> load() {
        CompletableFuture<Void> reset = this.publishReset();
        return this.commands.get()
                .hgetall(ONLINE_KEY)
                .toCompletableFuture()
                .thenApply(fields -> {
                    List<PlayerChangedMessage> records = new ArrayList<>(fields.size());
                    fields.forEach((key, value) -> {
                        ByteBuf buffer = Unpooled.wrappedBuffer(value);
                        try {
                            records.add(PlayerChangedMessage.readRecord(UUIDUtils.fromBytes(key), buffer));
                        } finally {
                            buffer.release();
                        }
                    });
                    return records;
                })
                .thenCombine(reset, (records, ignored) -> records)
                .thenAccept(records -> {
                    synchronized (this) {
                        if (this.closed) return;
                        List<RedisMessage<FriendlyByteBuf>> changes = this.loading;
                        this.loading = null;
                        int size = records.size();
                        List<NameEntry> names = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            PlayerChangedMessage record = records.get(i);
                            OnlineEntry entry = OnlineEntry.of(record.uniqueId(), record.serverId(), record.name(), record.locale(), null);
                            this.byUuid.put(record.uniqueId(), entry);
                            this.addCount(record.serverId(), 1);
                            names.add(entry.nameEntry());
                        }
                        this.byName.rebuild(names);
                        // loading 已置空, 回放的消息直接写入名单
                        size = changes.size();
                        for (int i = 0; i < size; i++) {
                            this.receive(changes.get(i));
                        }
                    }
                });
    }

    // 登录阶段刷新 Redis 的记录.
    @NotNull
    CompletableFuture<Long> publishLogin(@NotNull UUID uniqueId, @NotNull String name) {
        OnlineEntry previous = this.byUuid.get(uniqueId);
        return this.publishRecord(LOGIN_SCRIPT, uniqueId, name, previous == null ? Locale.US : previous.player().locale());
    }

    // 刷新 Redis 的记录, Join 和语言变化共用.
    void publishUpdate(@NotNull UUID uniqueId, @NotNull String name, @NotNull Locale locale) {
        this.publishRecord(UPDATE_SCRIPT, uniqueId, name, locale).whenComplete((ignored, error) -> {
            if (error != null) {
                this.plugin.logger().warn("Failed to publish player registry change", error);
            }
        });
    }

    // 刷新 Redis 里的记录, 并广播 PlayerChangedMessage.
    @NotNull
    private CompletableFuture<Long> publishRecord(String script, UUID uniqueId, String name, Locale locale) {
        ByteBuf buffer = Unpooled.buffer();
        byte[] value;
        try {
            PlayerChangedMessage.writeRecord(buffer, this.serverId, name, locale);
            value = ByteBufUtil.getBytes(buffer);
        } finally {
            buffer.release();
        }
        // 脚本直接发布消息字节, 目标服为空表示广播
        MessageBroker<FriendlyByteBuf> broker = this.plugin.messageBrokerManager().broker();
        PlayerChangedMessage message = new PlayerChangedMessage(uniqueId, this.serverId, name, locale);
        message.setTargetServer("");
        byte[][] arguments = {UUIDUtils.toBytes(uniqueId), this.serverPrefix, value, broker.channel(), broker.encode(message)};
        return this.commands.get()
                .<Long>eval(script, ScriptOutputType.INTEGER, new byte[][]{ONLINE_KEY}, arguments)
                .toCompletableFuture();
    }

    // 在玩家退出服务器时删除 Redis 里的记录.
    void publishQuit(@NotNull UUID uniqueId) {
        MessageBroker<FriendlyByteBuf> broker = this.plugin.messageBrokerManager().broker();
        PlayerChangedMessage message = new PlayerChangedMessage(uniqueId);
        message.setTargetServer("");
        byte[][] arguments = {UUIDUtils.toBytes(uniqueId), this.serverPrefix, broker.channel(), broker.encode(message)};
        this.commands.get()
                .<Long>eval(QUIT_SCRIPT, ScriptOutputType.INTEGER, new byte[][]{ONLINE_KEY}, arguments)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        this.plugin.logger().warn("Failed to publish player registry change", error);
                    }
                });
    }

    // 名单消息的唯一入口, 启动读取完成前先暂存
    public synchronized void receive(@NotNull RedisMessage<FriendlyByteBuf> message) {
        if (this.closed) return;
        if (this.loading != null) {
            this.loading.add(message);
            return;
        }
        if (message instanceof PlayerRegistryResetMessage reset) {
            this.reset(reset.serverId());
        }
        else if (message instanceof PlayerChangedMessage changed) {
            if (changed.serverId() == null) {
                this.remove(changed.uniqueId());
            } else {
                this.update(changed.uniqueId(), changed.serverId(), changed.name(), changed.locale());
            }
        }
    }

    // 以下写入方法由调用方持有名单锁
    private void update(UUID uniqueId, String serverId, String name, Locale locale) {
        OnlineEntry previous = this.byUuid.get(uniqueId);
        OnlineEntry entry = OnlineEntry.of(uniqueId, serverId, name, locale, previous);
        this.byUuid.put(uniqueId, entry);
        if (previous == null) {
            this.byName.insert(entry.nameEntry());
            this.addCount(serverId, 1);
            return;
        }
        // 切服和语言变化沿用名字条目, 名字索引不动
        if (previous.nameEntry() != entry.nameEntry()) {
            this.byName.remove(previous.nameEntry());
            this.byName.insert(entry.nameEntry());
        }
        if (!previous.serverId().equals(serverId)) {
            this.addCount(previous.serverId(), -1);
            this.addCount(serverId, 1);
        }
    }

    // 回放的删除可能对应快照里已经没有的记录
    private void remove(UUID uniqueId) {
        OnlineEntry previous = this.byUuid.remove(uniqueId);
        if (previous == null) return;
        this.byName.remove(previous.nameEntry());
        this.addCount(previous.serverId(), -1);
    }

    // concurrentutil 的 addTo / decFrom 只按引用比较键, 计数在名单锁内用按 equals 比较的 get / put / remove 维护
    private void addCount(String serverId, int delta) {
        int count = this.counts.get(serverId) + delta;
        if (count == 0) {
            this.counts.remove(serverId);
        } else {
            this.counts.put(serverId, count);
        }
    }

    private void reset(String serverId) {
        List<NameEntry> removed = this.byName.removeIf(nameEntry -> this.byUuid.get(nameEntry.uniqueId()).serverId().equals(serverId));
        int size = removed.size();
        for (int i = 0; i < size; i++) {
            this.byUuid.remove(removed.get(i).uniqueId());
        }
        this.counts.remove(serverId);
    }

    // 读取时只认存活服务器上的记录, 记录本身保留到该服务器重置
    @Nullable
    OfflineSparrowPlayer getGlobalOnlinePlayer(@NotNull UUID uniqueId) {
        OnlineEntry entry = this.byUuid.get(uniqueId);
        return entry != null && this.serverManager.isOnline(entry.serverId()) ? entry.player() : null;
    }

    @Nullable
    OfflineSparrowPlayer getGlobalOnlinePlayer(@NotNull String name) {
        return this.byName.find(name.toLowerCase(Locale.ROOT), nameEntry -> this.getGlobalOnlinePlayer(nameEntry.uniqueId()));
    }

    @NotNull
    List<OfflineSparrowPlayer> getGlobalOnlinePlayers() {
        List<OfflineSparrowPlayer> players = new ArrayList<>();
        this.byName.forEach("", nameEntry -> {
            OfflineSparrowPlayer player = this.getGlobalOnlinePlayer(nameEntry.uniqueId());
            if (player != null) {
                players.add(player);
            }
        });
        return players;
    }

    int getGlobalOnlineCount() {
        int count = 0;
        for (ConcurrentChainedObject2IntHashTable.TableEntry<String> entry : this.counts) {
            if (this.serverManager.isOnline(entry.getKey())) {
                count += entry.getValue();
            }
        }
        return count;
    }

    @NotNull
    public List<Suggestion> suggestGlobalPlayers(@NotNull String prefix) {
        List<Suggestion> suggestions = new ArrayList<>();
        this.byName.forEach(prefix.toLowerCase(Locale.ROOT), nameEntry -> {
            if (this.getCurrentServerId(nameEntry.uniqueId()) != null) {
                suggestions.add(nameEntry.suggestion());
            }
        });
        return suggestions;
    }

    @NotNull
    public List<Suggestion> suggestServerPlayers(@NotNull String serverId, @NotNull String prefix) {
        List<Suggestion> suggestions = new ArrayList<>();
        if (!this.serverManager.isOnline(serverId)) return suggestions;
        this.byName.forEach(prefix.toLowerCase(Locale.ROOT), nameEntry -> {
            OnlineEntry entry = this.byUuid.get(nameEntry.uniqueId());
            if (entry != null && entry.serverId().equals(serverId)) {
                suggestions.add(nameEntry.suggestion());
            }
        });
        return suggestions;
    }

    @Nullable
    String getCurrentServerId(@NotNull UUID uniqueId) {
        OnlineEntry entry = this.byUuid.get(uniqueId);
        return entry != null && this.serverManager.isOnline(entry.serverId()) ? entry.serverId() : null;
    }

    // 关服时先于服务器身份释放执行
    @NotNull
    synchronized CompletableFuture<Void> shutdown() {
        if (this.closed) return CompletableFuture.completedFuture(null);
        this.closed = true;
        // 启动读取完成后才删除本服记录
        boolean loaded = this.loading == null;
        this.loading = null;
        this.byUuid.clear();
        this.byName.clear();
        this.counts.clear();
        return loaded ? this.publishReset() : CompletableFuture.completedFuture(null);
    }

    // 启动加载和关服共用
    @NotNull
    private CompletableFuture<Void> publishReset() {
        MessageBroker<FriendlyByteBuf> broker = this.plugin.messageBrokerManager().broker();
        PlayerRegistryResetMessage message = new PlayerRegistryResetMessage(this.serverId);
        message.setTargetServer("");
        byte[][] arguments = {this.serverPrefix, broker.channel(), broker.encode(message)};
        return this.commands.get()
                .<Long>eval(RESET_SCRIPT, ScriptOutputType.INTEGER, new byte[][]{ONLINE_KEY}, arguments)
                .toCompletableFuture()
                .thenApply(ignored -> null);
    }

    private record OnlineEntry(@NotNull String serverId, @NotNull OfflineSparrowPlayer player, @NotNull NameEntry nameEntry) {

        // 名字不变时沿用名字条目, 名字和语言都不变时沿用玩家对象
        @NotNull
        static OnlineEntry of(@NotNull UUID uniqueId, @NotNull String serverId, @NotNull String name, @NotNull Locale locale, @Nullable OnlineEntry previous) {
            if (previous == null || !previous.player().name().equals(name)) {
                NameEntry nameEntry = new NameEntry(name.toLowerCase(Locale.ROOT), uniqueId, Suggestion.suggestion(name));
                return new OnlineEntry(serverId, new OfflineSparrowPlayer(uniqueId, name, locale), nameEntry);
            }
            OfflineSparrowPlayer player = previous.player().locale().equals(locale) ? previous.player() : new OfflineSparrowPlayer(uniqueId, name, locale);
            return new OnlineEntry(serverId, player, previous.nameEntry());
        }
    }
}
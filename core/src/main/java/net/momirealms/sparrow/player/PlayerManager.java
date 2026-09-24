package net.momirealms.sparrow.player;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.proxy.bukkit.entity.CraftPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.network.ConnectionProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.network.ServerCommonPacketListenerImplProxy;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerManager implements Listener, ChannelFutureListener {
    private final SparrowPlugin plugin;
    private final NetworkRoster roster;
    private final ConcurrentMap<Channel, BukkitSparrowPlayer> players = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, BukkitSparrowPlayer> onlinePlayers = new ConcurrentHashMap<>();

    public PlayerManager(@NotNull SparrowPlugin plugin) {
        this.plugin = plugin;
        this.roster = new NetworkRoster(plugin, this);
    }

    public void onEnable() {
        JavaPlugin javaPlugin = this.plugin.javaPlugin();
        Listener loginListener = VersionHelper.isPaper() ? new PaperPlayerListener(this) : new SpigotPlayerListener(this);
        javaPlugin.getServer().getPluginManager().registerEvents(loginListener, javaPlugin);
        javaPlugin.getServer().getPluginManager().registerEvents(this, javaPlugin);
        this.roster.onEnable();
    }

    /**
     * 为连接创建待加入的玩家对象, 同一 Channel 已有对象时复用已有对象.
     *
     * @param connection 本次登录的原版网络 Connection
     * @param uniqueId 本次登录的玩家 UUID
     * @param name 本次登录的玩家名
     * @return 此 Channel 对应的新建或已有玩家对象
     */
    @NotNull
    BukkitSparrowPlayer createPlayer(@NotNull ChannelHandler connection, @NotNull UUID uniqueId, @NotNull String name) {
        BukkitSparrowPlayer player = new BukkitSparrowPlayer(connection, uniqueId, name);
        Channel channel = player.nettyChannel();
        BukkitSparrowPlayer previous = this.players.putIfAbsent(channel, player);
        if (previous != null) return previous;
        channel.closeFuture().addListener(this);
        return player;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.players.computeIfPresent(this.getChannel(player), (channel, sparrowPlayer) -> {
            sparrowPlayer.initialize(player);
            this.onlinePlayers.put(sparrowPlayer.uniqueId(), sparrowPlayer);
            this.roster.presence(sparrowPlayer.uniqueId(), sparrowPlayer.name(), true);
            return sparrowPlayer;
        });
        // 记录当前名字, 供离线时按名字或 UUID 互查
        String name = player.getName();
        this.plugin.dataStorage().saveUser(player.getUniqueId(), name).whenComplete((ignored, failure) -> {
            if (failure != null) {
                this.plugin.logger().warn(TranslationManager.console(LogConstants.PLAYER_SAVE_FAILED, name), failure);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        this.removePlayer(this.getChannel(event.getPlayer()));
    }

    @Override
    public void operationComplete(@NotNull ChannelFuture future) {
        this.removePlayer(future.channel());
    }

    private void removePlayer(@NotNull Channel channel) {
        // 与 Join 对同一条目的初始化串行执行, 关闭连接时一并清理在线索引和全服名单.
        this.players.computeIfPresent(channel, (key, player) -> {
            if (this.onlinePlayers.remove(player.uniqueId(), player)) {
                this.roster.presence(player.uniqueId(), player.name(), false);
            }
            player.close();
            key.closeFuture().removeListener(this);
            return null;
        });
    }

    // 需要在 Redis 连接关闭前调用
    @ApiStatus.Internal
    public void shutdown() {
        this.roster.shutdown();
        for (Channel channel : this.players.keySet()) {
            this.removePlayer(channel);
        }
        this.onlinePlayers.clear();
        this.players.clear();
    }

    /**
     * 按 UUID 查找已完成 Join 初始化的玩家.
     *
     * @param uniqueId 玩家 UUID
     * @return 已加入且尚未清理的玩家对象, 不存在时为 null
     */
    @Nullable
    public SparrowPlayer getPlayer(@NotNull UUID uniqueId) {
        return this.onlinePlayers.get(uniqueId);
    }

    /**
     * 查找绑定到指定 Bukkit 实例的已初始化玩家, 旧连接的实例无法匹配重连后的对象.
     *
     * @param player 待查询的 Bukkit 玩家实例
     * @return 与该实例绑定的玩家对象, 未初始化、已清理或实例不匹配时为 null
     */
    @Nullable
    public BukkitSparrowPlayer getPlayer(@NotNull Player player) {
        BukkitSparrowPlayer sparrowPlayer = this.onlinePlayers.get(player.getUniqueId());
        return sparrowPlayer != null && sparrowPlayer.platformPlayer() == player ? sparrowPlayer : null;
    }

    /**
     * 按 Channel 查找玩家, 包含已创建但尚未完成 Join 初始化的对象.
     *
     * @param channel 本次连接使用的通道
     * @return 该连接的玩家对象, 尚未创建或已清理时为 null
     */
    @Nullable
    public BukkitSparrowPlayer getPlayer(@NotNull Channel channel) {
        return this.players.get(channel);
    }

    /**
     * 通过玩家的游戏阶段包监听器取得底层 Channel.
     *
     * @param player <strong>已经建立游戏阶段包监听器的 Bukkit 玩家</strong>
     * @return 游戏连接使用的通道
     */
    @NotNull
    private Channel getChannel(@NotNull Player player) {
        Object handle = CraftPlayerProxy.INSTANCE.getHandle(player);
        Object listener = ServerPlayerProxy.INSTANCE.getConnection(handle);
        Object connection = ServerCommonPacketListenerImplProxy.INSTANCE.getConnection(listener);
        return (Channel) ConnectionProxy.INSTANCE.getChannel(connection);
    }

    /**
     * 返回当前在线索引中玩家对象的只读列表快照.
     * 返回后列表成员固定, 其中的玩家对象仍可能退出; 并发加入或退出可能影响本次收集结果.
     *
     * @return 无固定顺序的玩家列表, 为空时返回空列表
     */
    @NotNull
    public Collection<SparrowPlayer> getOnlinePlayers() {
        return List.copyOf(this.onlinePlayers.values());
    }

    /**
     * 返回全服在线玩家, 按名字忽略大小写排序. 名单由进退服通知和每 30 秒的校准维护, 可能短暂落后于实际状态.
     *
     * @return 最近一次名单变化时的只读快照
     */
    @NotNull
    public List<NetworkPlayer> getNetworkPlayers() {
        return this.roster.players();
    }

    /**
     * 按名字在全服在线名单中查找玩家, 名字忽略大小写.
     *
     * @param name 玩家名
     * @return 在线玩家及其所在服务器, 不在线时为 null
     */
    @Nullable
    public NetworkPlayer getNetworkPlayer(@NotNull String name) {
        return this.roster.player(name);
    }

    /**
     * 按 UUID 在全服在线名单中查找玩家.
     *
     * @param uniqueId 玩家 UUID
     * @return 在线玩家及其所在服务器, 不在线时为 null
     */
    @Nullable
    public NetworkPlayer getNetworkPlayer(@NotNull UUID uniqueId) {
        return this.roster.player(uniqueId);
    }

    /**
     * 按前缀返回全服在线玩家名的命令补全项, 前缀忽略大小写, 空前缀返回全部.
     *
     * @param prefix 已输入的名字前缀
     * @return 按名字排序的补全项
     */
    @NotNull
    public List<Suggestion> suggestNetworkPlayers(@NotNull String prefix) {
        return this.roster.suggestions(prefix);
    }

    /**
     * 按名字解析玩家, 离线玩家也能查到. 全服在线时名字忽略大小写, 离线时按数据库记录精确匹配.
     *
     * @param name 玩家名
     * @return 解析任务, 找不到玩家时结果为空, 数据库出错时异常完成
     */
    @NotNull
    public CompletableFuture<Optional<PlayerIdentity>> resolvePlayer(@NotNull String name) {
        NetworkPlayer online = this.roster.player(name);
        if (online != null) return CompletableFuture.completedFuture(Optional.of(new PlayerIdentity(online.uuid(), online.name())));
        return this.plugin.dataStorage().lookupUser(name).thenApply(found -> found.map(uuid -> new PlayerIdentity(uuid, name)));
    }

    /**
     * 按 UUID 解析玩家, 离线时取数据库中最近使用的名字.
     *
     * @param uniqueId 玩家 UUID
     * @return 解析任务, 找不到玩家时结果为空, 数据库出错时异常完成
     */
    @NotNull
    public CompletableFuture<Optional<PlayerIdentity>> resolvePlayer(@NotNull UUID uniqueId) {
        NetworkPlayer online = this.roster.player(uniqueId);
        if (online != null) return CompletableFuture.completedFuture(Optional.of(new PlayerIdentity(online.uuid(), online.name())));
        return this.plugin.dataStorage().lookupName(uniqueId).thenApply(found -> found.map(name -> new PlayerIdentity(uniqueId, name)));
    }
}

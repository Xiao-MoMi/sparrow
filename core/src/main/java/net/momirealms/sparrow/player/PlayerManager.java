package net.momirealms.sparrow.player;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.player.cluster.ClusterPlayer;
import net.momirealms.sparrow.player.cluster.ClusterRoster;
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
    private final ClusterRoster cluster;
    private final ConcurrentMap<Channel, PlayerConnection> connections = new ConcurrentHashMap<>(); // 配置阶段起登记, 连接关闭或退出时移除
    private final ConcurrentMap<UUID, BukkitSparrowPlayer> players = new ConcurrentHashMap<>();     // Join 时创建, 退出时移除

    public PlayerManager(@NotNull SparrowPlugin plugin) {
        this.plugin = plugin;
        this.cluster = new ClusterRoster(plugin, this);
    }

    public void onEnable() {
        JavaPlugin javaPlugin = this.plugin.javaPlugin();
        Listener loginListener = VersionHelper.isPaper() ? new PaperLoginListener(this) : new SpigotLoginListener(this);
        javaPlugin.getServer().getPluginManager().registerEvents(loginListener, javaPlugin);
        javaPlugin.getServer().getPluginManager().registerEvents(this, javaPlugin);
        this.cluster.onEnable();
    }

    // 登录监听器在配置阶段调用, 同一 Channel 已登记时沿用已有连接.
    void registerConnection(@NotNull ChannelHandler handle, @NotNull UUID uniqueId, @NotNull String name) {
        PlayerConnection connection = new PlayerConnection(handle, uniqueId, name);
        // 关闭监听放在 map 操作之外. 已关闭的 Channel 可能在当前线程立即回调并移除这一条目
        if (this.connections.putIfAbsent(connection.channel(), connection) == null) {
            connection.channel().closeFuture().addListener(this);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ChannelHandler handle = this.connectionHandle(player);
        // 配置阶段未登记的连接在这里补登记
        this.registerConnection(handle, player.getUniqueId(), player.getName());
        // 与连接关闭的清理串行执行, 连接已经移除时不会创建玩家
        this.connections.computeIfPresent((Channel) ConnectionProxy.INSTANCE.getChannel(handle), (channel, connection) -> {
            this.players.put(connection.uniqueId(), new BukkitSparrowPlayer(connection, player));
            this.cluster.presence(connection.uniqueId(), connection.name(), true);
            return connection;
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
        this.removeConnection((Channel) ConnectionProxy.INSTANCE.getChannel(this.connectionHandle(event.getPlayer())));
    }

    @Override
    public void operationComplete(@NotNull ChannelFuture future) {
        this.removeConnection(future.channel());
    }

    // 移除连接时一并移除该连接上已加入的玩家, 并通知集群名单.
    private void removeConnection(@NotNull Channel channel) {
        this.connections.computeIfPresent(channel, (key, connection) -> {
            BukkitSparrowPlayer player = this.players.get(connection.uniqueId());
            if (player != null && player.connection() == connection && this.players.remove(connection.uniqueId(), player)) {
                this.cluster.presence(connection.uniqueId(), connection.name(), false);
            }
            key.closeFuture().removeListener(this);
            return null;
        });
    }

    // 需要在 Redis 连接关闭前调用
    @ApiStatus.Internal
    public void shutdown() {
        this.cluster.shutdown();
        for (Channel channel : this.connections.keySet()) {
            this.removeConnection(channel);
        }
        this.players.clear();
        this.connections.clear();
    }

    // 读取玩家游戏阶段包监听器持有的原版 Connection
    @NotNull
    private ChannelHandler connectionHandle(@NotNull Player player) {
        Object handle = CraftPlayerProxy.INSTANCE.getHandle(player);
        Object listener = ServerPlayerProxy.INSTANCE.getConnection(handle);
        return (ChannelHandler) ServerCommonPacketListenerImplProxy.INSTANCE.getConnection(listener);
    }

    /**
     * 按 UUID 查找已加入本服的玩家.
     *
     * @param uniqueId 玩家 UUID
     * @return 已加入且尚未退出的玩家, 不存在时为 null
     */
    @Nullable
    public SparrowPlayer getPlayer(@NotNull UUID uniqueId) {
        return this.players.get(uniqueId);
    }

    /**
     * 查找绑定到指定 Bukkit 实例的玩家, 旧连接的实例无法匹配重连后的对象.
     *
     * @param player 待查询的 Bukkit 玩家实例
     * @return 与该实例绑定的玩家, 尚未加入、已退出或实例不匹配时为 null
     */
    @Nullable
    public BukkitSparrowPlayer getPlayer(@NotNull Player player) {
        BukkitSparrowPlayer sparrowPlayer = this.players.get(player.getUniqueId());
        return sparrowPlayer != null && sparrowPlayer.platformPlayer() == player ? sparrowPlayer : null;
    }

    /**
     * 返回已加入本服玩家的只读列表快照. 列表成员固定, 其中的玩家仍可能随后退出.
     *
     * @return 无固定顺序的玩家列表
     */
    @NotNull
    public Collection<SparrowPlayer> getOnlinePlayers() {
        return List.copyOf(this.players.values());
    }

    /**
     * 按 Channel 查找连接, 包含已进入配置阶段但尚未 Join 的玩家.
     *
     * @param channel 本次连接使用的通道
     * @return 该通道的连接, 尚未登记或已移除时为 null
     */
    @Nullable
    public PlayerConnection getConnection(@NotNull Channel channel) {
        return this.connections.get(channel);
    }

    /**
     * 返回集群在线名单, 用于查找其他服务器上的玩家.
     *
     * @return 本插件实例的集群名单
     */
    @NotNull
    public ClusterRoster cluster() {
        return this.cluster;
    }

    /**
     * 按名字解析玩家, 离线玩家也能查到. 集群在线时名字忽略大小写, 离线时按数据库记录精确匹配.
     *
     * @param name 玩家名
     * @return 解析任务, 找不到玩家时结果为空, 数据库出错时异常完成
     */
    @NotNull
    public CompletableFuture<Optional<PlayerRef>> resolvePlayer(@NotNull String name) {
        ClusterPlayer online = this.cluster.find(name);
        if (online != null) return CompletableFuture.completedFuture(Optional.of(online.ref()));
        return this.plugin.dataStorage().lookupUser(name).thenApply(found -> found.map(uuid -> new PlayerRef(uuid, name)));
    }

    /**
     * 按 UUID 解析玩家, 离线时取数据库中最近使用的名字.
     *
     * @param uniqueId 玩家 UUID
     * @return 解析任务, 找不到玩家时结果为空, 数据库出错时异常完成
     */
    @NotNull
    public CompletableFuture<Optional<PlayerRef>> resolvePlayer(@NotNull UUID uniqueId) {
        ClusterPlayer online = this.cluster.find(uniqueId);
        if (online != null) return CompletableFuture.completedFuture(Optional.of(online.ref()));
        return this.plugin.dataStorage().lookupName(uniqueId).thenApply(found -> found.map(name -> new PlayerRef(uniqueId, name)));
    }
}

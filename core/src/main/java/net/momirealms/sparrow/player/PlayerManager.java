package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.player.cluster.ClusterPlayer;
import net.momirealms.sparrow.player.cluster.ClusterRoster;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.parser.ServerParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.player.teleport.TeleportManager;
import net.momirealms.sparrow.player.teleport.TeleportService;
import net.momirealms.sparrow.proxy.bukkit.entity.CraftPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.network.ConnectionProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.network.ServerCommonPacketListenerImplProxy;
import net.momirealms.sparrow.util.IpRange;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayerManager implements Listener, ChannelFutureListener {
    private final SparrowPlugin plugin;
    private final ClusterRoster cluster;
    private final TeleportManager teleports;
    private final TeleportService teleportService;
    // 配置阶段起登记, 连接关闭时移除
    private final ConcurrentChainedObject2ObjectHashTable<Channel, PlayerConnection> connections = new ConcurrentChainedObject2ObjectHashTable<>();
    // Join 时创建, 退出时移除
    private final ConcurrentChainedObject2ObjectHashTable<UUID, BukkitSparrowPlayer> players = new ConcurrentChainedObject2ObjectHashTable<>();
    private final List<PlayerListener> listeners = new CopyOnWriteArrayList<>();

    public PlayerManager(@NotNull SparrowPlugin plugin) {
        this.plugin = plugin;
        this.cluster = new ClusterRoster(plugin, this);
        this.teleports = new TeleportManager(plugin);
        this.teleportService = new TeleportService();
    }

    public void onEnable() {
        JavaPlugin javaPlugin = this.plugin.javaPlugin();
        javaPlugin.getServer().getMessenger().registerOutgoingPluginChannel(javaPlugin, ServerParser.CHANNEL);
        this.teleports.onEnable();
        Listener loginListener = VersionHelper.hasPaperPatch ? new PaperLoginListener(this) : new SpigotLoginListener(this);
        javaPlugin.getServer().getPluginManager().registerEvents(loginListener, javaPlugin);
        javaPlugin.getServer().getPluginManager().registerEvents(this, javaPlugin);
        javaPlugin.getServer().getPluginManager().registerEvents(this.teleportService, javaPlugin);
        this.cluster.onEnable();
    }

    public void registerListener(@NotNull PlayerListener listener) {
        this.listeners.add(listener);
    }

    public void unregisterListener(@NotNull PlayerListener listener) {
        this.listeners.remove(listener);
    }

    // 登录监听器在配置阶段调用, 同一 Channel 已登记时沿用已有连接.
    void registerConnection(@NotNull ChannelHandler handle, @NotNull UUID uniqueId, @NotNull String name) {
        PlayerConnection connection = new PlayerConnection(handle, uniqueId, name);
        // 关闭监听放在 map 操作之外. 已关闭的 Channel 可能在当前线程立即回调并移除这一条目
        if (this.connections.putIfAbsent(connection.channel(), connection) == null) {
            connection.channel().closeFuture().addListener(this);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        for (PlayerListener listener : this.listeners) {
            if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
            listener.onPreLogin(event);
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
        this.teleports.onJoin(player);
        // 登录刷新名字、时间和 IP, 下线位置由 Quit 事件保存.
        String name = player.getName();
        InetSocketAddress address = player.getAddress();
        long ip = address == null ? IpRange.NONE : IpRange.address(address.getAddress());
        this.plugin.dataStorage().saveLogin(player.getUniqueId(), name, ip, System.currentTimeMillis())
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        this.plugin.logger().warn(LogConstants.PLAYER_SAVE_FAILED, failure, name);
                    }
                });
        // 本插件的进服处理全部完成后再通知
        BukkitSparrowPlayer joined = this.getPlayer(player);
        if (joined != null) {
            for (PlayerListener listener : this.listeners) {
                listener.onJoin(joined);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BukkitSparrowPlayer leaving = this.getPlayer(player);
        if (leaving != null) {
            for (PlayerListener listener : this.listeners) {
                listener.onQuit(leaving);
            }
        }
        this.teleportService.onQuit(player.getUniqueId());
        String name = player.getName();
        this.plugin.dataStorage()
                .saveLogout(
                        player.getUniqueId(),
                        name,
                        System.currentTimeMillis(),
                        ServerConfig.serverId(),
                        WorldLocation.from(player.getLocation())
                )
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        this.plugin.logger().warn(LogConstants.PLAYER_SAVE_FAILED, failure, name);
                    }
                });
        if (leaving != null && this.players.remove(player.getUniqueId(), leaving) == leaving) {
            this.cluster.presence(leaving.uniqueId(), leaving.name(), false);
        }
    }


    /**
     * 按名字解析玩家, 离线玩家也能查到.
     * 在线时名字忽略大小写, 离线时按数据库记录精确匹配.
     *
     * @param name 玩家名
     * @return 解析任务, 找不到玩家时结果为空, 数据库出错时异常完成
     */
    @NotNull
    public CompletableFuture<Optional<PlayerRef>> resolvePlayer(@NotNull String name) {
        ClusterPlayer online = this.cluster.find(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new PlayerRef(online.uuid(), online.name())));
        }
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
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new PlayerRef(online.uuid(), online.name())));
        }
        return this.plugin.dataStorage().lookupName(uniqueId).thenApply(found -> found.map(name -> new PlayerRef(uniqueId, name)));
    }

    @Override
    public void operationComplete(@NotNull ChannelFuture future) {
        this.removeConnection(future.channel());
    }

    private void removeConnection(@NotNull Channel channel) {
        this.connections.remove(channel);
        channel.closeFuture().removeListener(this);
    }

    public void shutdown() {
        this.teleportService.shutdown();
        this.teleports.shutdown();
        this.cluster.shutdown();
        for (Channel channel : this.connections.keys()) {
            this.removeConnection(channel);
        }
        this.players.clear();
        this.connections.clear();
    }

    @NotNull
    private ChannelHandler connectionHandle(@NotNull Player player) {
        Object handle = CraftPlayerProxy.INSTANCE.getHandle(player);
        Object listener = ServerPlayerProxy.INSTANCE.getConnection(handle);
        return (ChannelHandler) ServerCommonPacketListenerImplProxy.INSTANCE.getConnection(listener);
    }

    @Nullable
    public SparrowPlayer getPlayer(@NotNull UUID uniqueId) {
        return this.players.get(uniqueId);
    }

    @Nullable
    public BukkitSparrowPlayer getPlayer(@NotNull Player player) {
        BukkitSparrowPlayer sparrowPlayer = this.players.get(player.getUniqueId());
        return sparrowPlayer != null && sparrowPlayer.platformPlayer() == player ? sparrowPlayer : null;
    }

    @NotNull
    public Collection<SparrowPlayer> getOnlinePlayers() {
        return List.copyOf(this.players.values());
    }

    @Nullable
    public PlayerConnection getConnection(@NotNull Channel channel) {
        return this.connections.get(channel);
    }

    @NotNull
    public ClusterRoster cluster() {
        return this.cluster;
    }

    @NotNull
    public TeleportManager teleports() {
        return this.teleports;
    }

    @NotNull
    public TeleportService teleportService() {
        return this.teleportService;
    }
}
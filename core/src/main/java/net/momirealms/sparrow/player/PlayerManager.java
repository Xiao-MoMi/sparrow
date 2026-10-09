package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayerManager implements Listener, ChannelFutureListener {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    // 配置阶段起登记, 连接关闭时移除
    private final ConcurrentChainedObject2ObjectHashTable<Channel, PlayerConnection> connections = new ConcurrentChainedObject2ObjectHashTable<>();
    // Join 时创建, 退出时移除
    private final ConcurrentChainedObject2ObjectHashTable<UUID, SparrowPlayer> players = new ConcurrentChainedObject2ObjectHashTable<>();
    private final List<PlayerListener> listeners = new CopyOnWriteArrayList<>();

    public void onEnable() {
        JavaPlugin javaPlugin = this.plugin.javaPlugin();
        Listener loginListener = VersionHelper.hasPaperPatch ? new PaperLoginListener(this) : new SpigotLoginListener(this);
        javaPlugin.getServer().getPluginManager().registerEvents(loginListener, javaPlugin);
        javaPlugin.getServer().getPluginManager().registerEvents(this, javaPlugin);
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
            this.players.put(connection.uniqueId(), new SparrowPlayer(connection, player));
            return connection;
        });
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
        SparrowPlayer joined = this.getPlayer(player);
        if (joined != null) {
            for (PlayerListener listener : this.listeners) {
                listener.onJoin(joined);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        SparrowPlayer leaving = this.getPlayer(player);
        if (leaving != null) {
            for (PlayerListener listener : this.listeners) {
                listener.onQuit(leaving);
            }
        }
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
        if (leaving != null) {
            this.players.remove(player.getUniqueId(), leaving);
        }
    }

    @Override
    public void operationComplete(@NotNull ChannelFuture future) {
        this.removeConnection(future.channel());
    }

    private void removeConnection(@NotNull Channel channel) {
        this.connections.remove(channel);
        channel.closeFuture().removeListener(this);
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
    public SparrowPlayer getPlayer(@NotNull Player player) {
        SparrowPlayer sparrowPlayer = this.players.get(player.getUniqueId());
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

    public void shutdown() {
        for (Channel channel : this.connections.keys()) {
            this.removeConnection(channel);
        }
        this.players.clear();
        this.connections.clear();
    }
}
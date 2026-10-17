package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import net.momirealms.sparrow.player.listener.PlayerJoinListener;
import net.momirealms.sparrow.player.listener.PlayerPreLoginListener;
import net.momirealms.sparrow.player.listener.PlayerQuitListener;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.proxy.minecraft.network.ConnectionProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.network.ServerCommonPacketListenerImplProxy;
import net.momirealms.sparrow.proxy.paper.connection.ReadablePlayerCookieConnectionProxy;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.util.HandlerList;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PlayerManager implements Listener, ChannelFutureListener {
    public final SparrowPlugin plugin = SparrowPlugin.instance();
    public final PlayerCache playerCache = new PlayerCache();
    private final ConcurrentChainedObject2ObjectHashTable<Channel, PlayerConnection> connections = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, BukkitSparrowPlayer> localPlayers = new ConcurrentChainedObject2ObjectHashTable<>();
    private final HandlerList<PlayerPreLoginListener> preLoginListeners = new HandlerList<>();
    private final HandlerList<PlayerJoinListener> joinListeners = new HandlerList<>();
    private final HandlerList<PlayerQuitListener> quitListeners = new HandlerList<>();

    public void onEnable() {
        JavaPlugin javaPlugin = this.plugin.javaPlugin();
        javaPlugin.getServer().getPluginManager().registerEvents(this, javaPlugin);
        this.playerCache.refreshFromRedis();
    }

    // 根据 Player 查询本服在线 SparrowPlayer 玩家实例.
    @Nullable
    public BukkitSparrowPlayer getOnlinePlayer(@NotNull Player player) {
        BukkitSparrowPlayer current = this.getOnlinePlayer(player.getUniqueId());
        return current != null && current.platformPlayer == player ? current : null;
    }

    // 根据 UUID 查询本服在线 SparrowPlayer 玩家实例.
    @Nullable
    public BukkitSparrowPlayer getOnlinePlayer(@NotNull UUID uniqueId) {
        return this.localPlayers.get(uniqueId);
    }

    // 根据 Name 精确查询本服在线玩家, 区分大小写.
    @Nullable
    public BukkitSparrowPlayer getOnlinePlayer(@NotNull String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            return this.localPlayers.get(player.getUniqueId());
        }
        return null;
    }

    // 收集本服玩家对象, 返回只读列表副本.
    @NotNull
    public Collection<BukkitSparrowPlayer> getOnlinePlayers() {
        List<BukkitSparrowPlayer> local = new ArrayList<>(this.localPlayers.values());
        return Collections.unmodifiableCollection(local);
    }

    // 根据 UUID 查询全服在线 SparrowPlayer 玩家实例.
    public OfflineSparrowPlayer getGlobalOnlinePlayer(@NotNull UUID uniqueId) {
        BukkitSparrowPlayer onlinePlayer = this.getOnlinePlayer(uniqueId);
        if (onlinePlayer != null) return onlinePlayer;
        return this.playerCache.getGlobalOnlinePlayer(uniqueId);
    }

    // 根据 Name 查询全服在线 SparrowPlayer 玩家实例.
    public OfflineSparrowPlayer getGlobalOnlinePlayer(@NotNull String playerName) {
        BukkitSparrowPlayer onlinePlayer = this.getOnlinePlayer(playerName);
        if (onlinePlayer != null) return onlinePlayer;
        return this.playerCache.getGlobalOnlinePlayer(playerName);
    }

    // 收集全服在线玩家对象, 返回只读列表副本.
    public Collection<OfflineSparrowPlayer> getGlobalOnlinePlayers() {
        return this.playerCache.globalOnlinePlayers();
    }

    // 按 UUID 优先查询在线玩家, 离线时异步读取数据库中的名字.
    @NotNull
    public CompletableFuture<Optional<SparrowPlayer>> getPlayer(@NotNull UUID uniqueId) {
        SparrowPlayer online = this.getGlobalOnlinePlayer(uniqueId);
        if (online != null) return CompletableFuture.completedFuture(Optional.of(online));
        return this.plugin.dataStorage().lookupName(uniqueId)
                .thenApply(found -> found.map(name -> this.getOrCreate(uniqueId, name)));
    }

    // 按名字优先查询在线玩家, 离线时沿用数据库的名字匹配规则, 未找到时结果为空.
    @NotNull
    public CompletableFuture<Optional<SparrowPlayer>> getPlayer(@NotNull String playerName) {
        SparrowPlayer online = this.getGlobalOnlinePlayer(playerName);
        if (online != null) return CompletableFuture.completedFuture(Optional.of(online));
        return this.plugin.dataStorage().lookupUser(playerName)
                .thenApply(found -> found.map(uniqueId -> this.getOrCreate(uniqueId, playerName)));
    }

    // 使用已有身份资料取得玩家对象, 优先返回管理器中的当前实例.
    @NotNull
    SparrowPlayer getOrCreate(@NotNull UUID uniqueId, @NotNull String name) {
        BukkitSparrowPlayer current = this.localPlayers.get(uniqueId);
        if (current != null) return current;
        OfflineSparrowPlayer cached = this.playerCache.getGlobalOnlinePlayer(uniqueId);
        return cached != null ? cached : new OfflineSparrowPlayer(uniqueId, name);
    }

    // 查询指定 Channel 在预登录阶段建立的连接登记.
    @Nullable
    public PlayerConnection getConnection(@NotNull Channel channel) {
        return this.connections.get(channel);
    }

    @NotNull
    public Subscription registerPreLoginListener(@NotNull PlayerPreLoginListener listener) {
        return this.preLoginListeners.register(listener);
    }

    @NotNull
    public Subscription registerPreLoginListener(@NotNull PlayerPreLoginListener listener, @NotNull EventPriority priority) {
        return this.preLoginListeners.register(listener, priority);
    }

    @NotNull
    public Subscription registerJoinListener(@NotNull PlayerJoinListener listener) {
        return this.joinListeners.register(listener);
    }

    @NotNull
    public Subscription registerJoinListener(@NotNull PlayerJoinListener listener, @NotNull EventPriority priority) {
        return this.joinListeners.register(listener, priority);
    }

    @NotNull
    public Subscription registerQuitListener(@NotNull PlayerQuitListener listener) {
        return this.quitListeners.register(listener);
    }

    @NotNull
    public Subscription registerQuitListener(@NotNull PlayerQuitListener listener, @NotNull EventPriority priority) {
        return this.quitListeners.register(listener, priority);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        this.preLoginListeners.dispatch(listener -> listener.onPreLogin(event));
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        this.registerConnection(
                (ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection()),
                event.getUniqueId(),
                event.getName()
        );
    }

    // 在 Join 阶段创建本服玩家本体.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Object listener = ServerPlayerProxy.INSTANCE.getConnection(((CraftPlayer) player).getHandle());
        ChannelHandler handle = (ChannelHandler) ServerCommonPacketListenerImplProxy.INSTANCE.getConnection(listener);
        // 同一 Channel 的 Join 和连接移除通过连接表的原子操作串行处理.
        this.connections.computeIfPresent((Channel) ConnectionProxy.INSTANCE.getChannel(handle), (channel, connection) -> {
            this.localPlayers.put(connection.uniqueId(), new BukkitSparrowPlayer(connection, player));
            return connection;
        });
        BukkitSparrowPlayer joined = this.getOnlinePlayer(player);
        if (joined != null) {
            this.joinListeners.dispatch(joinListener -> joinListener.onJoin(joined));
        }
    }

    // 释放退出本服的玩家实例
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 按实例移除本次会话, 同一 UUID 的新会话保持有效.
        BukkitSparrowPlayer leaving = this.getOnlinePlayer(player);
        if (leaving == null) return;
        if (this.localPlayers.remove(player.getUniqueId(), leaving) == leaving) {
            this.quitListeners.dispatch(listener -> listener.onQuit(leaving));
        }
    }

    // 登记一次本服登录的网络连接, 将登记的存活期绑定到 Channel 关闭事件.
    private void registerConnection(ChannelHandler handle, UUID uniqueId, String name) {
        PlayerConnection connection = new PlayerConnection(handle, uniqueId, name);
        // 登记完成后添加关闭监听, 已完成的关闭 Future 可能立即执行清理回调.
        if (this.connections.putIfAbsent(connection.channel(), connection) == null) {
            connection.channel().closeFuture().addListener(this);
        }
    }

    // 在 Channel 关闭任务完成后释放对应的连接登记.
    @Override
    public void operationComplete(@NotNull ChannelFuture future) {
        Channel channel = future.channel();
        this.connections.remove(channel);
        channel.closeFuture().removeListener(this);
    }


    // 结束当前管理器的生命周期, 关闭集群缓存, 清空本服玩家对象并解除连接关闭监听.
    public void shutdown() {
        this.preLoginListeners.clear();
        this.joinListeners.clear();
        this.quitListeners.clear();
        this.playerCache.shutdown();
        this.localPlayers.clear();
        for (Channel channel : this.connections.keys()) {
            this.connections.remove(channel);
            channel.closeFuture().removeListener(this);
        }
        this.connections.clear();
    }
}
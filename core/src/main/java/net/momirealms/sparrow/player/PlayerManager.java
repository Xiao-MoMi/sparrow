package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.data.access.SingleWriterData;
import net.momirealms.sparrow.player.listener.PlayerJoinListener;
import net.momirealms.sparrow.player.listener.PlayerPreLoginListener;
import net.momirealms.sparrow.player.listener.PlayerPrepareListener;
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
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PlayerManager implements Listener, ChannelFutureListener {
    public final SparrowPlugin plugin = SparrowPlugin.instance();
    public final PlayerRegistry registry = new PlayerRegistry();

    private final ConcurrentChainedObject2ObjectHashTable<Channel, PlayerConnection> connections = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, PlayerConnection> registrations = new ConcurrentChainedObject2ObjectHashTable<>(); // 每个 UUID 在本服当前登记名单的连接
    private final ConcurrentChainedObject2ObjectHashTable<UUID, BukkitSparrowPlayer> localPlayers = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, PlayerConnection> lockHolders = new ConcurrentChainedObject2ObjectHashTable<>(); // 本服持有玩家锁的连接
    private final PlayerLock lock = new PlayerLock();
    private final LoginPacketGate gate = new LoginPacketGate();
    private final PlayerProfileService profiles = new PlayerProfileService();
    private final HandlerList<PlayerPrepareListener> prepareListeners = new HandlerList<>();
    private final HandlerList<PlayerPreLoginListener> preLoginListeners = new HandlerList<>();
    private final HandlerList<PlayerJoinListener> joinListeners = new HandlerList<>();
    private final HandlerList<PlayerQuitListener> quitListeners = new HandlerList<>();
    private final Set<PlayerConnection> sessions = new HashSet<>(); // 在管理器锁下登记, 直到收尾完成才移除
    private volatile boolean closing;

    public void onLoad() {
        this.registry.load().join();
    }

    public void onEnable() {
        this.profiles.onEnable();
        JavaPlugin javaPlugin = this.plugin.javaPlugin();
        javaPlugin.getServer().getPluginManager().registerEvents(this, javaPlugin);
        this.gate.onEnable();
        this.plugin.scheduler().asyncRepeating(() -> {
            if (this.lockHolders.isEmpty()) return;
            this.lock.renew(this.lockHolders.keys()).whenComplete((ignored, error) -> {
                if (error != null) {
                    this.plugin.logger().warn("Failed to renew player locks", error);
                }
            });
        }, 5, 5, TimeUnit.SECONDS);
    }

    // 登记本服在线名单. 同一 UUID 的登记和关闭清理都在 compute 内提交脚本, Redis 按提交顺序执行
    @NotNull
    synchronized CompletableFuture<Void> register(@NotNull PlayerConnection connection) {
        if (this.closing || !this.sessions.contains(connection)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player connection is closing"));
        }
        CompletableFuture<Void> registered = new CompletableFuture<>();
        this.registrations.compute(connection.uniqueId(), (uniqueId, previous) -> {
            this.registry.publishLogin(uniqueId, connection.name()).whenComplete((ignored, error) -> {
                if (error == null) {
                    registered.complete(null);
                } else {
                    registered.completeExceptionally(error);
                }
            });
            return connection;
        });
        return registered;
    }

    // 返回的 Future 同时供登录门控与连接收尾等待, 调用方不能直接改变它的结果
    @NotNull
    synchronized CompletableFuture<Void> prepare(@NotNull PlayerConnection connection, long deadlineNanos) {
        if (this.closing || connection.ending() != null || !this.sessions.contains(connection)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player connection is closing"));
        }
        if (connection.preparation() != null) return connection.preparation();
        UUID uniqueId = connection.uniqueId();
        CompletableFuture<Void> preparation = this.lock.acquire(uniqueId, deadlineNanos)
                .thenCompose(ignored -> {
                    this.lockHolders.put(uniqueId, connection);
                    // 业务准备回调执行前, 档案写容器已加载
                    return this.profiles.prepare(connection);
                })
                .thenComposeAsync(ignored -> {
                    List<CompletableFuture<Void>> preparing = new ArrayList<>();
                    this.prepareListeners.dispatch(listener -> preparing.add(CompletableFuture.completedFuture(connection).thenCompose(listener::onPrepare)));
                    return CompletableFuture.allOf(preparing.toArray(CompletableFuture[]::new));
                }, this.plugin.scheduler().async())
                .thenRun(() -> connection.prepared(true));
        connection.preparation(preparation);
        return preparation;
    }

    // 在锁内登记一次收尾结果, 退出和释放回调在锁外执行
    private CompletableFuture<Void> end(PlayerConnection connection, @Nullable BukkitSparrowPlayer player) {
        CompletableFuture<Void> ending;
        CompletableFuture<Void> preparation;
        synchronized (this) {
            if (connection.ending() != null) return connection.ending();
            ending = new CompletableFuture<>();
            connection.ending(ending);
            preparation = connection.preparation();
        }
        CompletableFuture.completedFuture(null)
                .thenCompose(ignored -> {
                    if (player != null) return this.quit(player);
                    if (preparation == null) return CompletableFuture.completedFuture(null);
                    return preparation.handle((result, error) -> null)
                            .thenCompose(result -> this.lockHolders.get(connection.uniqueId()) == connection ? this.release(connection) : CompletableFuture.completedFuture(null));
                })
                .whenComplete((ignored, error) -> {
                    synchronized (this) {
                        this.sessions.remove(connection);
                    }
                    if (error == null) {
                        ending.complete(null);
                    } else {
                        ending.completeExceptionally(error);
                    }
                });
        return ending;
    }

    // 等全部退出监听器结束后释放连接. 每个监听器在各自的 thenCompose 中调用, 同步抛出的异常只让它自己的 Future 失败
    private CompletableFuture<Void> quit(BukkitSparrowPlayer player) {
        this.profiles.onQuit(player);
        List<CompletableFuture<Void>> quitting = new ArrayList<>();
        this.quitListeners.dispatch(listener -> quitting.add(CompletableFuture.completedFuture(player).thenCompose(listener::onQuit)));
        return CompletableFuture.allOf(quitting.toArray(CompletableFuture[]::new))
                .handle((ignored, error) -> {
                    if (error != null) {
                        this.plugin.logger().warn("Failed to run quit listeners for " + player.name(), error);
                    }
                    return null;
                })
                .thenCompose(ignored -> this.release(player.connection));
    }

    // 业务释放回调结束后保存并释放档案, 最后归还持续续期的玩家锁
    private CompletableFuture<Void> release(PlayerConnection connection) {
        List<CompletableFuture<Void>> releasing = new ArrayList<>();
        this.prepareListeners.dispatch(listener -> releasing.add(CompletableFuture.completedFuture(connection).thenCompose(listener::onRelease)));
        return CompletableFuture.allOf(releasing.toArray(CompletableFuture[]::new))
                .handle((ignored, error) -> {
                    if (error != null) {
                        this.plugin.logger().warn("Failed to run release listeners for " + connection.name(), error);
                    }
                    return null;
                })
                .thenCompose(ignored -> this.profiles.release(connection))
                .handle((ignored, error) -> {
                    if (error != null) {
                        this.plugin.logger().warn("Failed to release player profile for " + connection.name(), error);
                    }
                    return null;
                })
                .thenCompose(ignored -> this.lock.release(connection.uniqueId()))
                .whenComplete((ignored, error) -> {
                    this.lockHolders.remove(connection.uniqueId(), connection);
                    if (error != null) {
                        this.plugin.logger().warn("Failed to release player lock for " + connection.name(), error);
                    }
                });
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
        return this.registry.getGlobalOnlinePlayer(uniqueId);
    }

    // 根据 Name 查询全服在线 SparrowPlayer 玩家实例.
    public OfflineSparrowPlayer getGlobalOnlinePlayer(@NotNull String playerName) {
        BukkitSparrowPlayer onlinePlayer = this.getOnlinePlayer(playerName);
        if (onlinePlayer != null) return onlinePlayer;
        return this.registry.getGlobalOnlinePlayer(playerName);
    }

    // 收集全服在线玩家对象, 返回只读列表副本.
    @NotNull
    public List<OfflineSparrowPlayer> getGlobalOnlinePlayers() {
        return this.registry.getGlobalOnlinePlayers();
    }

    // 获取全服在线玩家数量.
    public int getGlobalOnlinePlayerCount() {
        return this.registry.getGlobalOnlineCount();
    }

    // 按 UUID 优先查询在线玩家, 离线时通过档案读副本取得名字.
    @NotNull
    public CompletableFuture<Optional<SparrowPlayer>> getPlayer(@NotNull UUID uniqueId) {
        SparrowPlayer online = this.getGlobalOnlinePlayer(uniqueId);
        if (online != null) return CompletableFuture.completedFuture(Optional.of(online));
        return this.profiles.data().reader(uniqueId).read().thenApply(entries -> {
            PlayerProfile profile = entries.get(PlayerProfile.ENTRY);
            return profile == null ? Optional.empty() : Optional.of(this.getOrCreate(uniqueId, profile.name()));
        });
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
        OfflineSparrowPlayer cached = this.registry.getGlobalOnlinePlayer(uniqueId);
        return cached != null ? cached : new OfflineSparrowPlayer(uniqueId, name);
    }

    // 查询指定 Channel 在预登录阶段建立的连接登记.
    @Nullable
    public PlayerConnection getConnection(@NotNull Channel channel) {
        return this.connections.get(channel);
    }

    @NotNull
    public SingleWriterData<UUID, String, PlayerProfile> profileData() {
        return this.profiles.data();
    }

    @NotNull
    public Subscription registerPrepareListener(@NotNull PlayerPrepareListener listener) {
        return this.prepareListeners.register(listener);
    }

    @NotNull
    public Subscription registerPrepareListener(@NotNull PlayerPrepareListener listener, @NotNull EventPriority priority) {
        return this.prepareListeners.register(listener, priority);
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
        if (this.closing) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Server is shutting down"));
            return;
        }
        this.preLoginListeners.dispatch(listener -> listener.onPreLogin(event));
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (!this.registerConnection(
                (ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection()),
                event.getUniqueId(),
                event.getName(),
                event.getAddress()
        )) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Server is shutting down"));
        }
    }

    // 在 Join 阶段创建本服玩家本体.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Object listener = ServerPlayerProxy.INSTANCE.getConnection(((CraftPlayer) player).getHandle());
        ChannelHandler handle = (ChannelHandler) ServerCommonPacketListenerImplProxy.INSTANCE.getConnection(listener);
        // 同一 Channel 的 Join 和连接移除通过连接表的原子操作串行处理.
        this.connections.computeIfPresent((Channel) ConnectionProxy.INSTANCE.getChannel(handle), (channel, connection) -> {
            connection.joined(true);
            this.localPlayers.put(connection.uniqueId(), new BukkitSparrowPlayer(connection, player));
            return connection;
        });
        BukkitSparrowPlayer joined = this.getOnlinePlayer(player);
        if (joined != null) {
            this.profiles.onJoin(joined);
            this.registry.publishUpdate(joined.uniqueId(), joined.name(), joined.locale());
            this.joinListeners.dispatch(joinListener -> joinListener.onJoin(joined));
        }
    }

    // 事件早于 ServerPlayer 的语言字段更新, 新语言从事件中读取.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocaleChange(@NotNull PlayerLocaleChangeEvent event) {
        BukkitSparrowPlayer player = this.getOnlinePlayer(event.getPlayer());
        if (player == null) return;
        this.registry.publishUpdate(player.uniqueId(), player.name(), event.locale());
    }

    // 释放退出本服的玩家实例
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 按实例移除本次会话, 同一 UUID 的新会话保持有效.
        BukkitSparrowPlayer leaving = this.getOnlinePlayer(player);
        if (leaving == null) return;
        if (this.localPlayers.remove(player.getUniqueId(), leaving) == leaving) {
            this.registry.publishQuit(leaving.uniqueId());
            this.end(leaving.connection, leaving);
        }
    }

    // 停止接纳新连接与准备请求, 已接纳的会话继续完成准备和释放
    public synchronized void stopAccepting() {
        this.closing = true;
    }

    // 等待全部已接纳会话, 包括准备中, 未 Join 和已开始退出的连接
    public void closeSessions() {
        List<CompletableFuture<Void>> closing = new ArrayList<>();
        List<PlayerConnection> sessions;
        synchronized (this) {
            this.closing = true;
            sessions = List.copyOf(this.sessions);
        }
        for (PlayerConnection connection : sessions) {
            BukkitSparrowPlayer player = this.localPlayers.get(connection.uniqueId());
            if (player != null && player.connection == connection) {
                this.localPlayers.remove(connection.uniqueId(), player);
                closing.add(this.end(connection, player));
            } else {
                closing.add(this.end(connection, null));
            }
        }
        CompletableFuture.allOf(closing.toArray(CompletableFuture[]::new)).join();
    }

    // 登记一次本服登录的网络连接, 将登记的存活期绑定到 Channel 关闭事件.
    private synchronized boolean registerConnection(ChannelHandler handle, UUID uniqueId, String name, InetAddress ip) {
        if (this.closing) return false;
        PlayerConnection connection = new PlayerConnection(handle, uniqueId, name, ip);
        // 登记完成后添加关闭监听, 已完成的关闭 Future 可能立即执行清理回调.
        if (this.connections.putIfAbsent(connection.channel(), connection) == null) {
            this.sessions.add(connection);
            connection.channel().closeFuture().addListener(this);
        }
        return true;
    }

    // 在 Channel 关闭任务完成后释放对应的连接登记.
    @Override
    public void operationComplete(@NotNull ChannelFuture future) {
        Channel channel = future.channel();
        PlayerConnection connection = this.connections.remove(channel);
        channel.closeFuture().removeListener(this);
        if (connection == null) return;
        // 同一 UUID 的新连接已经登记时保留它的记录; 登记了但没进入游戏的连接不会触发 Quit, 在这里删除名单记录.
        this.registrations.compute(connection.uniqueId(), (uniqueId, current) -> {
            if (current != connection) return current;
            if (!connection.joined()) {
                this.registry.publishQuit(uniqueId);
            }
            return null;
        });
        // 进入过游戏的连接由 Quit 释放; 没进入游戏的连接就等准备结束, 取得过锁就释放
        if (!connection.joined()) {
            this.end(connection, null);
        }
    }

    // 关服时先于释放服务器身份调用, 删除本服的全服名单记录并等待完成.
    public void closeRegistry() {
        this.registry.shutdown().join();
    }

    // 结束当前管理器的生命周期, 清空本服玩家对象并解除连接关闭监听.
    public synchronized void shutdown() {
        this.prepareListeners.clear();
        this.preLoginListeners.clear();
        this.joinListeners.clear();
        this.quitListeners.clear();
        this.localPlayers.clear();
        for (Channel channel : this.connections.keys()) {
            this.connections.remove(channel);
            channel.closeFuture().removeListener(this);
        }
        this.connections.clear();
        this.registrations.clear();
    }
}
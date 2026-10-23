package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import net.momirealms.sparrow.data.access.MultiWriterData;
import net.momirealms.sparrow.data.access.SingleWriterData;
import net.momirealms.sparrow.data.access.SingleWriterEntries;
import net.momirealms.sparrow.data.definition.SingleWriterDefinition;
import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.player.listener.PlayerJoinListener;
import net.momirealms.sparrow.player.listener.PlayerPreLoginListener;
import net.momirealms.sparrow.player.listener.PlayerPrepareListener;
import net.momirealms.sparrow.player.listener.PlayerQuitListener;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.proxy.minecraft.network.ConnectionProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.network.ServerCommonPacketListenerImplProxy;
import net.momirealms.sparrow.proxy.paper.connection.ReadablePlayerCookieConnectionProxy;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.util.HandlerList;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.UUIDUtils;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private final List<PlayerDataBinding> bindings = new ArrayList<>(); // 只在插件启用阶段添加, 之后只读
    private final HandlerList<PlayerPrepareListener> prepareListeners = new HandlerList<>();
    private final HandlerList<PlayerPreLoginListener> preLoginListeners = new HandlerList<>();
    private final HandlerList<PlayerJoinListener> joinListeners = new HandlerList<>();
    private final HandlerList<PlayerQuitListener> quitListeners = new HandlerList<>();

    private SingleWriterData<UUID, String, PlayerProfile> profileData;
    private SchedulerTask autoSaveTask;
    private boolean autoSaveStopped;

    public void onLoad() {
        this.registry.load().join();
    }

    public void onEnable() {
        this.profileData = this.plugin.dataAccess().register(SingleWriterDefinition.of(
                SparrowKey.sparrow("player_profile"),
                DataCodec.of(UUIDUtils::toBytes, UUIDUtils::fromBytes),
                DataCodec.<String>of(entry -> entry.getBytes(StandardCharsets.UTF_8), bytes -> new String(bytes, StandardCharsets.UTF_8)),
                PlayerProfile.Codec.INSTANCE,
                this.plugin.dataStorage().playerProfileStore()
        ).redisTtl(Duration.ofMinutes(10)));
        this.registerData(this.profileData);
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
        this.scheduleAutoSave();
    }

    // 登记本服在线名单. 同一 UUID 的登记和关闭清理都在 compute 内提交脚本, Redis 按提交顺序执行
    @NotNull
    CompletableFuture<Void> register(@NotNull PlayerConnection connection) {
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

    // 取得玩家锁后加载托管数据, 再在插件异步线程执行准备监听器, 全部完成后连接进入已准备状态. 返回的 Future 也是连接收尾的等待点, 调用方不能直接改变它的结果
    @NotNull
    CompletableFuture<Void> prepare(@NotNull PlayerConnection connection, long deadlineNanos) {
        UUID uniqueId = connection.uniqueId();
        CompletableFuture<Void> preparation = this.lock.acquire(uniqueId, deadlineNanos)
                .thenCompose(ignored -> {
                    this.lockHolders.put(uniqueId, connection);
                    // 并行加载托管数据, 成功的记到连接上. allOf 等全部加载结束才完成, 失败时连接上正好留下加载成功的那些
                    CompletableFuture<?>[] loading = new CompletableFuture<?>[this.bindings.size()];
                    for (int i = 0; i < loading.length; i++) {
                        loading[i] = this.bindings.get(i).load(uniqueId).thenAccept(loaded -> connection.loaded.put(loaded.data(), loaded));
                    }
                    return CompletableFuture.allOf(loading);
                })
                .thenComposeAsync(ignored -> {
                    List<CompletableFuture<Void>> preparing = new ArrayList<>();
                    this.prepareListeners.dispatch(listener -> preparing.add(listener.onPrepare(connection)));
                    return CompletableFuture.allOf(preparing.toArray(CompletableFuture[]::new));
                }, this.plugin.scheduler().async())
                .thenRun(() -> connection.prepared(true));
        connection.preparation(preparation);
        return preparation;
    }

    // 等全部退出监听器结束后释放连接. 每个监听器在各自的 thenCompose 中调用, 同步抛出的异常只让它自己的 Future 失败
    private CompletableFuture<Void> quit(BukkitSparrowPlayer player) {
        long logoutAt = System.currentTimeMillis();
        ServerLocation location = player.getServerLocation();
        player.data(this.profileData).update(PlayerProfile.ENTRY, profile -> new PlayerProfile(profile.name(), profile.loginAt(), logoutAt, profile.ip(), location));
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

    // 执行释放监听器, 再把托管数据先保存后释放, 最后删除玩家锁. 删除完成前锁仍在续期, 这些步骤耗时不会让锁提前过期
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
                .thenCompose(ignored -> {
                    // 各类托管数据并行卸载, 保存失败只记录, 照样释放
                    List<CompletableFuture<Void>> unloading = new ArrayList<>();
                    for (PlayerDataBinding.Loaded loaded : connection.loaded.values()) {
                        unloading.add(loaded.save().get()
                                .handle((saved, error) -> {
                                    if (error != null) {
                                        this.plugin.logger().warn("Failed to save player data for " + connection.name(), error);
                                    }
                                    return null;
                                })
                                .thenCompose(saved -> loaded.release().get()));
                    }
                    return CompletableFuture.allOf(unloading.toArray(CompletableFuture[]::new));
                })
                .handle((ignored, error) -> {
                    if (error != null) {
                        this.plugin.logger().warn("Failed to release player data for " + connection.name(), error);
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
        return this.profileData.reader(uniqueId).read().thenApply(entries -> {
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
        return this.profileData;
    }

    /** 让单写数据随玩家进出本服加载和卸载, <strong>只能在插件启用阶段, 玩家进服前调用</strong>. */
    public synchronized <E, V> void registerData(@NotNull SingleWriterData<UUID, E, V> data) {
        this.bindings.add(new PlayerDataBinding.Single<>(data));
    }

    /** 让多写数据随玩家进出本服固定和关闭读副本, 约束同单写. */
    public synchronized <E, V> void registerData(@NotNull MultiWriterData<UUID, E, V> data) {
        this.bindings.add(new PlayerDataBinding.Multi<>(data));
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
        this.preLoginListeners.dispatch(listener -> listener.onPreLogin(event));
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        this.registerConnection(
                (ChannelHandler) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection()),
                event.getUniqueId(),
                event.getName(),
                event.getAddress()
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
            connection.joined(true);
            this.localPlayers.put(connection.uniqueId(), new BukkitSparrowPlayer(connection, player));
            return connection;
        });
        BukkitSparrowPlayer joined = this.getOnlinePlayer(player);
        if (joined != null) {
            SingleWriterEntries<String, PlayerProfile> entries = joined.data(this.profileData);
            long loginAt = System.currentTimeMillis();
            entries.update(PlayerProfile.ENTRY, previous -> new PlayerProfile(
                    joined.name(),
                    loginAt,
                    previous == null ? 0 : previous.logoutAt(),
                    joined.connection.ip(),
                    previous == null ? null : previous.logoutLocation()
            ));
            entries.publish();
            entries.save();
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
            this.quit(leaving);
        }
    }

    // 数据保存
    private synchronized void scheduleAutoSave() {
        if (this.autoSaveStopped) return;
        this.autoSaveTask = this.plugin.scheduler().asyncLater(this::saveDirtyData, PluginConfig.playerData().autoSaveIntervalSeconds(), TimeUnit.SECONDS);
    }

    private synchronized void saveDirtyData() {
        if (this.autoSaveStopped) return;
        CompletableFuture<?>[] saving = new CompletableFuture<?>[this.bindings.size()];
        for (int i = 0; i < saving.length; i++) {
            saving[i] = this.bindings.get(i).saveDirty();
        }
        // 整轮结束后再计下一周期, 已接受的保存由 data 自身跟踪并在关闭时等待
        CompletableFuture.allOf(saving).whenComplete((ignored, error) -> {
            if (error != null) {
                this.plugin.logger().warn("Failed to auto-save bound player data", error);
            }
            this.scheduleAutoSave();
        });
    }

    private synchronized void stopAutoSave() {
        this.autoSaveStopped = true;
        if (this.autoSaveTask != null) this.autoSaveTask.cancel();
    }

    // Paper 关服时先禁用插件再移除玩家, 本服在线玩家收不到 Quit, 在这里走完退出流程
    public void closeSessions() {
        this.stopAutoSave();
        List<CompletableFuture<Void>> closing = new ArrayList<>();
        for (BukkitSparrowPlayer player : this.localPlayers.values()) {
            if (this.localPlayers.remove(player.uniqueId(), player) == player) {
                closing.add(this.quit(player));
            }
        }
        CompletableFuture.allOf(closing.toArray(CompletableFuture[]::new)).join();
    }

    // 登记一次本服登录的网络连接, 将登记的存活期绑定到 Channel 关闭事件.
    private void registerConnection(ChannelHandler handle, UUID uniqueId, String name, InetAddress ip) {
        PlayerConnection connection = new PlayerConnection(handle, uniqueId, name, ip);
        // 登记完成后添加关闭监听, 已完成的关闭 Future 可能立即执行清理回调.
        if (this.connections.putIfAbsent(connection.channel(), connection) == null) {
            connection.channel().closeFuture().addListener(this);
        }
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
        if (!connection.joined() && connection.preparation() != null) {
            connection.preparation()
                    .handle((ignored, error) -> null)
                    .thenCompose(ignored -> this.lockHolders.get(connection.uniqueId()) == connection ? this.release(connection) : CompletableFuture.completedFuture(null));
        }
    }

    // 关服时先于释放服务器身份调用, 删除本服的全服名单记录并等待完成.
    public void closeRegistry() {
        this.registry.shutdown().join();
    }

    // 结束当前管理器的生命周期, 清空本服玩家对象并解除连接关闭监听.
    public void shutdown() {
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
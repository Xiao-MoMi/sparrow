package net.momirealms.sparrow.player;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerManager implements Listener, ChannelFutureListener {
    private final ConcurrentMap<Channel, BukkitSparrowPlayer> players = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, BukkitSparrowPlayer> onlinePlayers = new ConcurrentHashMap<>();

    public void onEnable(@NotNull JavaPlugin plugin) {
        Listener loginListener = VersionHelper.isPaper() ? new PaperPlayerListener(this) : new SpigotPlayerListener(this);
        plugin.getServer().getPluginManager().registerEvents(loginListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
            return sparrowPlayer;
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
        // 与 Join 对同一条目的初始化串行执行, 关闭连接时一并清理在线索引.
        this.players.computeIfPresent(channel, (key, player) -> {
            this.onlinePlayers.remove(player.uniqueId(), player);
            player.close();
            key.closeFuture().removeListener(this);
            return null;
        });
    }

    @ApiStatus.Internal
    public void shutdown() {
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
}

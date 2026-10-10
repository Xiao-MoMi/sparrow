package net.momirealms.sparrow.proxy.bungeecord;

import net.md_5.bungee.api.ServerConnectRequest;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.event.SettingsChangedEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.event.EventHandler;
import net.momirealms.sparrow.proxy.common.player.PlayerPresence;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerManager;
import net.momirealms.sparrow.proxy.common.player.ConnectResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class BungeeCordPlayerManager extends ProxyPlayerManager implements Listener {
    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, PendingConnection> connections = new ConcurrentHashMap<>();

    public BungeeCordPlayerManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void registerListeners() {
        this.plugin.getProxy().getPluginManager().registerListener(this.plugin, this);
    }

    @EventHandler
    public void onServerConnected(ServerSwitchEvent event) {
        this.directory.connected(event.getPlayer().getUniqueId());
        PendingConnection pending = this.connections.get(event.getPlayer().getUniqueId());
        if (pending != null) {
            boolean connected = event.getPlayer().getServer().getInfo().getName().equals(pending.server());
            pending.result().complete(connected ? ConnectResult.SUCCESS : ConnectResult.FAILED);
        }
    }

    @EventHandler
    public void onSettingsChanged(SettingsChangedEvent event) {
        this.directory.locale(event.getPlayer().getUniqueId(), event.getPlayer().getLocale());
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        this.directory.disconnected(event.getPlayer().getUniqueId());
        PendingConnection pending = this.connections.get(event.getPlayer().getUniqueId());
        if (pending != null) {
            pending.result().complete(ConnectResult.PLAYER_OFFLINE);
        }
    }

    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        PendingConnection pending = this.connections.get(event.getPlayer().getUniqueId());
        if (pending != null && event.getKickedFrom().getName().equals(pending.server())) {
            pending.result().complete(ConnectResult.FAILED);
        }
    }

    @Override
    public void disable() {
        this.plugin.getProxy().getPluginManager().unregisterListener(this);
        for (PendingConnection pending : this.connections.values()) {
            pending.result().complete(ConnectResult.FAILED);
        }
    }

    @Override
    @NotNull
    public List<PlayerPresence> getPlayers() {
        List<PlayerPresence> players = new ArrayList<>();
        for (ProxiedPlayer player : this.plugin.getProxy().getPlayers()) {
            PlayerPresence presence = this.findPlayer(player.getUniqueId());
            if (presence != null) {
                players.add(presence);
            }
        }
        return players;
    }

    @Override
    @Nullable
    public PlayerPresence findPlayer(@NotNull UUID uuid) {
        ProxiedPlayer player = this.plugin.getProxy().getPlayer(uuid);
        if (player == null || !player.isConnected()) return null;
        Server server = player.getServer();
        if (server == null) return null;
        // 首次连接子服时, 客户端可能尚未发送语言设置.
        Locale locale = player.getLocale();
        return new PlayerPresence(uuid, player.getName(), server.getInfo().getName(), locale == null ? Locale.ROOT : locale);
    }

    @Override
    @NotNull
    public CompletableFuture<ConnectResult> connect(@NotNull UUID uuid, @NotNull String sourceServer, @NotNull String targetServer) {
        ProxiedPlayer player = this.plugin.getProxy().getPlayer(uuid);
        if (player == null || !player.isConnected()) return CompletableFuture.completedFuture(ConnectResult.PLAYER_OFFLINE);
        ServerInfo target = this.plugin.getProxy().getServerInfo(targetServer);
        if (target == null) return CompletableFuture.completedFuture(ConnectResult.SERVER_NOT_FOUND);
        Server current = player.getServer();
        if (current == null || !current.getInfo().getName().equals(sourceServer) || sourceServer.equals(targetServer)) {
            return CompletableFuture.completedFuture(ConnectResult.FAILED);
        }
        PendingConnection pending = new PendingConnection(targetServer, new CompletableFuture<>());
        if (this.connections.putIfAbsent(uuid, pending) != null) return CompletableFuture.completedFuture(ConnectResult.FAILED);
        pending.result().orTimeout(5, TimeUnit.SECONDS).whenComplete((result, error) -> this.connections.remove(uuid, pending));
        player.connect(ServerConnectRequest.builder()
                .target(target)
                .reason(ServerConnectEvent.Reason.PLUGIN)
                .retry(false)
                .callback((result, error) -> {
                    // 原生 SUCCESS 只确认 TCP 连接, 切服完成由 ServerSwitchEvent 确认.
                    if (result == ServerConnectRequest.Result.SUCCESS) return;
                    if (error != null) {
                        this.plugin.getLogger().log(Level.WARNING, "Failed to connect " + uuid + " to " + targetServer, error);
                    }
                    pending.result().complete(ConnectResult.FAILED);
                })
                .build()
        );
        return pending.result();
    }

    @Override
    public boolean disconnect(@NotNull UUID player, @NotNull String jsonReason) {
        ProxiedPlayer target = this.plugin.getProxy().getPlayer(player);
        if (target == null || !target.isConnected()) return false;
        target.disconnect(ComponentSerializer.deserialize(jsonReason));
        return true;
    }

    private record PendingConnection(String server, CompletableFuture<ConnectResult> result) {
    }
}
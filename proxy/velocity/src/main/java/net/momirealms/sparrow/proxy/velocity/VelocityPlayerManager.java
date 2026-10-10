package net.momirealms.sparrow.proxy.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.momirealms.sparrow.proxy.common.player.PlayerPresence;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerManager;
import net.momirealms.sparrow.proxy.common.player.ConnectResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class VelocityPlayerManager extends ProxyPlayerManager {
    private final VelocitySparrow plugin;
    private final ProxyServer server;

    public VelocityPlayerManager(@NotNull VelocitySparrow plugin, @NotNull ProxyServer server) {
        this.plugin = plugin;
        this.server = server;
    }

    @Override
    protected void registerListeners() {
        this.server.getEventManager().register(this.plugin, this);
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        this.directory.connected(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onSettingsChanged(PlayerSettingsChangedEvent event) {
        this.directory.locale(event.getPlayer().getUniqueId(), event.getPlayerSettings().getLocale());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player current = this.server.getPlayer(event.getPlayer().getUniqueId()).orElse(null);
        if (current != null && current != event.getPlayer()) return;
        this.directory.disconnected(event.getPlayer().getUniqueId());
    }

    @Override
    public void disable() {
        this.server.getEventManager().unregisterListener(this.plugin, this);
    }

    @Override
    @NotNull
    public List<PlayerPresence> getPlayers() {
        List<PlayerPresence> players = new ArrayList<>();
        for (Player player : this.server.getAllPlayers()) {
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
        Player player = this.server.getPlayer(uuid).orElse(null);
        if (player == null || !player.isActive()) return null;
        ServerConnection server = player.getCurrentServer().orElse(null);
        if (server == null) return null;
        return new PlayerPresence(uuid, player.getUsername(), server.getServerInfo().getName(), player.getPlayerSettings().getLocale());
    }

    @Override
    @NotNull
    public CompletableFuture<ConnectResult> connect(@NotNull UUID uuid, @NotNull String sourceServer, @NotNull String targetServer) {
        Player player = this.server.getPlayer(uuid).orElse(null);
        if (player == null || !player.isActive()) return CompletableFuture.completedFuture(ConnectResult.PLAYER_OFFLINE);
        RegisteredServer target = this.server.getServer(targetServer).orElse(null);
        if (target == null) return CompletableFuture.completedFuture(ConnectResult.SERVER_NOT_FOUND);
        ServerConnection current = player.getCurrentServer().orElse(null);
        if (current == null || !current.getServerInfo().getName().equals(sourceServer) || sourceServer.equals(targetServer)) {
            return CompletableFuture.completedFuture(ConnectResult.FAILED);
        }
        return player.createConnectionRequest(target).connectWithIndication()
                .handle((connected, error) -> {
                    if (error != null) {
                        this.plugin.logger().warn("Failed to connect " + uuid + " to " + targetServer, error);
                        return ConnectResult.FAILED;
                    }
                    ServerConnection destination = player.getCurrentServer().orElse(null);
                    return connected && destination != null && destination.getServerInfo().getName().equals(targetServer)
                            ? ConnectResult.SUCCESS
                            : ConnectResult.FAILED;
                });
    }

    @Override
    public boolean disconnect(@NotNull UUID player, @NotNull String jsonReason) {
        Player target = this.server.getPlayer(player).orElse(null);
        if (target == null || !target.isActive()) return false;
        target.disconnect(GsonComponentSerializer.gson().deserialize(jsonReason));
        return true;
    }
}
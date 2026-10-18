package net.momirealms.sparrow.proxy.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerManager;
import net.momirealms.sparrow.proxy.common.player.ConnectResult;
import org.jetbrains.annotations.NotNull;

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
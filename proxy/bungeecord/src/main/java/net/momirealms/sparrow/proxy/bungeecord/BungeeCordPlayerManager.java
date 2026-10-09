package net.momirealms.sparrow.proxy.bungeecord;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.event.SettingsChangedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.event.EventHandler;
import net.momirealms.sparrow.proxy.common.player.PlayerPresence;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BungeeCordPlayerManager extends ProxyPlayerManager implements Listener {
    private final Plugin plugin;

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
    }

    @EventHandler
    public void onSettingsChanged(SettingsChangedEvent event) {
        this.directory.locale(event.getPlayer().getUniqueId(), event.getPlayer().getLocale());
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        this.directory.disconnected(event.getPlayer().getUniqueId());
    }

    @Override
    public void disable() {
        this.plugin.getProxy().getPluginManager().unregisterListener(this);
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
    public boolean disconnect(@NotNull UUID player, @NotNull String jsonReason) {
        ProxiedPlayer target = this.plugin.getProxy().getPlayer(player);
        if (target == null || !target.isConnected()) return false;
        target.disconnect(ComponentSerializer.deserialize(jsonReason));
        return true;
    }
}
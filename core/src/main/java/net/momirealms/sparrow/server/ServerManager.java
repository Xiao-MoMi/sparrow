package net.momirealms.sparrow.server;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ServerManager {
    public final SparrowPlugin plugin = SparrowPlugin.instance();
    public final String serverId = ServerConfig.serverId();
    public final ServerRegistry registry = new ServerRegistry();
    public final long startedAt = System.currentTimeMillis();

    private final StatusRefreshListener statusRefreshListener = new StatusRefreshListener();
    private @Nullable SchedulerTask heartbeatTask;

    public void onLoad() {
        this.registry.onLoad();
        this.heartbeatTask = this.plugin.scheduler().asyncRepeating(this::heartbeat, 1, 1, TimeUnit.SECONDS);
        this.registry.refreshFromRedis().join();
    }

    public void onEnable() {
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this.statusRefreshListener, this.plugin.javaPlugin());
    }

    public void onServerLoaded() {
        this.statusRefreshListener.onServerLoaded();
        this.heartbeat();
    }

    public synchronized void reload() {
        if (this.registry.closing()) return;
        this.plugin.scheduler().async().execute(this::heartbeat);
    }

    public synchronized void heartbeat() {
        String[] worlds = this.statusRefreshListener.worlds;
        ServerStatus status = worlds == null ? null : new ServerStatus(this.serverId, this.startedAt, Bukkit.getMaxPlayers(), Bukkit.getName(), Arrays.asList(worlds), ServerConfig.tags());
        this.registry.heartbeat(status);
    }

    @Nullable
    public ServerStatus getServer(@NotNull String serverId) {
        return this.registry.getServer(serverId);
    }

    @NotNull
    public List<ServerStatus> getServers() {
        return this.registry.getServers();
    }

    @Nullable
    public ServerStatus getOnlineServer(@NotNull String serverId) {
        return this.registry.getOnlineServer(serverId);
    }

    @NotNull
    public List<ServerStatus> getOnlineServers() {
        return this.registry.getOnlineServers();
    }

    @NotNull
    public List<ServerStatus> getOnlineServersByTag(@NotNull String tag) {
        return this.registry.getOnlineServersByTag(tag);
    }

    @NotNull
    public List<ServerStatus> getOnlineServersByTags(@NotNull Set<String> tags) {
        return this.registry.getOnlineServersByTags(tags);
    }

    public boolean isOnline(@NotNull String serverId) {
        return this.registry.isOnline(serverId);
    }

    public void shutdown() {
        CompletableFuture<Void> removal;
        synchronized (this) {
            if (this.heartbeatTask != null) this.heartbeatTask.cancel();
            removal = this.registry.shutdown();
        }
        removal.join();
    }
}
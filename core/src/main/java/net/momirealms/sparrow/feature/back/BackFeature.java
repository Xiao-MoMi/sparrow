package net.momirealms.sparrow.feature.back;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class BackFeature extends Feature<BackSettings> implements Listener, PlayerListener {
    public static final String ID = "back";
    private static final double MIN_DISTANCE_SQUARED = 1.0;  // 原地转视角这类短距离传送不记录

    private final SparrowPlugin plugin;
    private final ConcurrentChainedObject2ObjectHashTable<UUID, WorldLocation> points = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, CompletableFuture<Void>> deathWrites = new ConcurrentChainedObject2ObjectHashTable<>();
    private volatile Set<TeleportCause> causes = Set.of();

    public BackFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        BackSettings settings = this.plugin.configurationManager().featuresConfig().config().back();
        this.causes = parseCauses(settings.teleportCauses());
        super.config = settings;
    }

    @Override
    protected void onLoad() {
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        this.plugin.playerManager().registerListener(this);
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new BackCommand(this.plugin.commandManager(), this.plugin));
        register.accept(new DeathBackCommand(this.plugin.commandManager(), this.plugin));
    }

    @Override
    protected void onDisable() {
        this.points.clear();
    }

    @Override
    protected void onUnload() {
        this.plugin.playerManager().unregisterListener(this);
    }

    @Override
    public void onQuit(@NotNull SparrowPlayer player) {
        this.points.remove(player.uniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(@NotNull PlayerTeleportEvent event) {
        if (!this.enabled() || !this.causes.contains(event.getCause())) {
            return;
        }
        // 还没完成进服处理, 或者还在进服保护期内, 这时的传送多半是其他插件把玩家送到出生点
        SparrowPlayer player = this.plugin.playerManager().getPlayer(event.getPlayer());
        if (player == null || System.currentTimeMillis() - player.connection().connectedAt() < super.config.joinGraceSeconds() * 1000L) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == to.getWorld() && from.distanceSquared(to) < MIN_DISTANCE_SQUARED) {
            return;
        }
        this.points.put(player.uniqueId(), WorldLocation.from(from));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(@NotNull PlayerDeathEvent event) {
        if (!this.enabled() || !super.config.recordDeath()) {
            return;
        }
        Player player = event.getEntity();
        UUID uniqueId = player.getUniqueId();
        String name = player.getName();
        WorldLocation location = WorldLocation.from(player.getLocation());
        CompletableFuture<Void> write = this.plugin.dataStorage().saveDeath(uniqueId, name, System.currentTimeMillis(), ServerConfig.serverId(), location);
        this.deathWrites.put(uniqueId, write);
        write.whenComplete((ignored, failure) -> {
            this.deathWrites.remove(uniqueId, write);
            if (failure != null) {
                this.plugin.logger().warn("Failed to save death location for " + name, failure);
            }
        });
    }

    @Nullable
    public WorldLocation point(@NotNull UUID player) {
        return this.points.get(player);
    }

    @NotNull
    public CompletableFuture<Optional<PlayerData>> loadDeath(@NotNull UUID player) {
        CompletableFuture<Void> write = this.deathWrites.get(player);
        CompletableFuture<Void> ready = write == null ? CompletableFuture.completedFuture(null) : write;
        return ready.thenCompose(ignored -> this.plugin.dataStorage()
                .loadPlayer(player)
                .whenComplete((found, failure) -> {
                    if (failure != null) {
                        this.plugin.logger().warn("Failed to load death location for " + player, failure);
                    }
                }));
    }

    /**
     * 判断玩家是否刚从下线记录里的服务器切换过来, 是的话 /back 可以回到那里.
     * 重新登录的玩家下线时间远早于连上本服的时间, 不算切服.
     */
    public boolean switchedFrom(@NotNull SparrowPlayer player, @NotNull PlayerData data) {
        if (data.lastLogoutLocation() == null || data.lastLogoutServer() == null) {
            return false;
        }
        if (ServerConfig.serverId().equals(data.lastLogoutServer())) {
            return false;
        }
        // 代理先让玩家进入新服务器再断开旧服务器, 下线时间可能略晚于连上本服的时间
        return Math.abs(data.lastLogout() - player.connection().connectedAt()) <= super.config.serverSwitchWindowSeconds() * 1000L;
    }

    // 配置写错时在启用前报告
    private static Set<TeleportCause> parseCauses(List<String> names) {
        Set<TeleportCause> causes = EnumSet.noneOf(TeleportCause.class);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            try {
                causes.add(TeleportCause.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("back.teleport-causes contains an unknown cause: " + name, exception);
            }
        }
        return causes;
    }
}

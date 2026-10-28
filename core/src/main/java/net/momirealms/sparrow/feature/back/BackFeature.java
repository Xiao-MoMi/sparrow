package net.momirealms.sparrow.feature.back;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.BackStore;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.FeaturePermission;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.teleport.event.TeleportArriveEvent;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.util.ThrowableUtils;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class BackFeature implements Feature, Listener {
    public static final String ID = "back";
    public static final FeaturePermission COMMAND_PERMISSION = new FeaturePermission(ID);

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final BackOperations operations = new BackOperations();
    private volatile BackConfig config;
    private volatile boolean enabled;
    private long generation;
    private BackService back;
    private DeathBackService deathback;
    private Subscription quitSubscription;

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supportsHotEnable() {
        return true;
    }

    @Override
    public boolean supportsHotDisable() {
        return true;
    }

    private BackConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/back.yaml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(BackSettings.class, BackSettings::new)
                    .load(path)
                    .value()
                    .toConfig();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onEnable() {
        BackConfig loaded = this.readConfig();
        if (this.back == null) {
            BackStore store = this.plugin.dataStorage().backStore();
            this.back = new BackService(this, store, this.operations);
            this.deathback = new DeathBackService(this, store, this.operations);
            this.quitSubscription = this.plugin.playerManager().registerQuitListener(player -> this.operations.await(player.uniqueId()));
        }
        this.config = loaded;
        this.enabled = true;
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    synchronized CompletableFuture<@Nullable Component> back(BukkitSparrowPlayer player, boolean unsafe) {
        if (!this.enabled) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_DISABLED);
        return this.back.teleport(player, this.config.back().select(player.platformPlayer), this.generation, unsafe);
    }

    synchronized CompletableFuture<@Nullable Component> deathback(BukkitSparrowPlayer player, boolean unsafe) {
        if (!this.enabled) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_DISABLED);
        return this.deathback.teleport(player, this.config.deathback().select(player.platformPlayer), this.generation, unsafe);
    }

    synchronized <T> CompletableFuture<T> withPlayer(BukkitSparrowPlayer player, long generation, Supplier<CompletableFuture<T>> operation) {
        if (!this.enabled || this.generation != generation || this.plugin.playerManager().getOnlinePlayer(player.platformPlayer) != player) {
            return CompletableFuture.failedFuture(new CancellationException("Back request session is no longer active"));
        }
        return operation.get();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public synchronized void onTeleport(@NotNull PlayerTeleportEvent event) {
        if (!this.enabled) return;
        BukkitSparrowPlayer player = this.plugin.playerManager().getOnlinePlayer(event.getPlayer());
        if (player == null) return;
        ServerLocation from = ServerLocation.from(ServerConfig.serverId(), event.getFrom());
        ServerLocation to = ServerLocation.from(ServerConfig.serverId(), event.getTo());
        if (!this.shouldRecord(from, to, event.getCause(), player.connection.connectedAt(), System.currentTimeMillis())) return;
        this.back.record(player, from).whenComplete((ignored, error) -> {
            if (error != null) this.plugin.logger().warn("Failed to record back location for " + player.name(), ThrowableUtils.unwrap(error));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public synchronized void onDeath(@NotNull PlayerDeathEvent event) {
        if (!this.enabled || !this.config.recordDeath()) return;
        BukkitSparrowPlayer player = this.plugin.playerManager().getOnlinePlayer(event.getEntity());
        if (player == null) return;
        this.deathback.record(player, player.getServerLocation()).whenComplete((ignored, error) -> {
            if (error != null) this.plugin.logger().warn("Failed to record death location for " + player.name(), ThrowableUtils.unwrap(error));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public synchronized void onArrive(@NotNull TeleportArriveEvent event) {
        if (!this.enabled) return;
        BukkitSparrowPlayer player = this.plugin.playerManager().getOnlinePlayer(event.playerId());
        if (player == null) return;
        if (!this.shouldRecord(event.from(), event.to(), TeleportCause.PLUGIN, event.sourceConnectedAt(), event.departedAt())) return;
        // 在事件返回前登记异步写入, 本服后续查询与退出等待即可覆盖这次操作
        this.back.record(player, event.from()).whenComplete((ignored, error) -> {
            if (error != null) this.plugin.logger().warn("Failed to record back location for " + player.name(), ThrowableUtils.unwrap(error));
        });
    }

    private boolean shouldRecord(ServerLocation from, ServerLocation to, TeleportCause cause, long connectedAt, long departedAt) {
        BackConfig current = this.config;
        if (!current.causes().contains(cause)) return false;
        if (departedAt - connectedAt < current.joinGraceMillis()) return false;
        if (!from.server().equals(to.server()) || !from.world().equals(to.world())) return true;
        double x = from.x() - to.x();
        double y = from.y() - to.y();
        double z = from.z() - to.z();
        return x * x + y * y + z * z >= 1.0;
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onDisable() {
        this.enabled = false;
        this.generation++;
        HandlerList.unregisterAll(this);
        return this.operations.awaitAll();
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onPluginShutdown() {
        if (this.quitSubscription != null) this.quitSubscription.close();
        return this.operations.awaitAll();
    }
}
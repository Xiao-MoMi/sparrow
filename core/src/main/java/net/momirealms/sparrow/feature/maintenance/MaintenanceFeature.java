package net.momirealms.sparrow.feature.maintenance;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MaintenanceFeature implements Feature {
    public static final String ID = "maintenance";
    public static final String BYPASS_PERMISSION = DependencyVersions.PROJECT_ID + ".bypass.maintenance";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final UUID bossBarId = UUID.randomUUID();
    private final Set<UUID> bossBarViewers = new HashSet<>();
    private MaintenanceSettings config;
    private volatile boolean active;
    private Subscription preLoginSubscription;
    private Subscription joinSubscription;
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

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onEnable() {
        this.config = this.readConfig();
        this.active = this.config.active();
        this.preLoginSubscription = this.plugin.playerManager().registerPreLoginListener(this::onPreLogin);
        this.joinSubscription = this.plugin.playerManager().registerJoinListener(this::apply);
        this.quitSubscription = this.plugin.playerManager().registerQuitListener(this::onQuit);
        this.refreshPlayers();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    private YamlMapper<MaintenanceSettings> createMapper() {
        return YamlMapperFactory.builder()
                .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                .build()
                .create(MaintenanceSettings.class, MaintenanceSettings::new);
    }

    @NotNull
    private MaintenanceSettings readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/maintenance.yml");
        MaintenanceSettings settings;
        try {
            settings = this.createMapper().load(path).value();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
        settings.validate();
        return settings;
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        this.active = this.config.active();
        this.refreshPlayers();
        return CompletableFuture.completedFuture(null);
    }

    public synchronized void setActive(boolean active) {
        this.config.active(active);
        Path path = this.plugin.dataFolderPath().resolve("features/maintenance.yml");
        try {
            this.createMapper().save(path, this.config);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save " + path, exception);
        }
        this.active = active;
        this.refreshPlayers();
    }

    public boolean active() {
        return this.active;
    }

    private void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        if (!this.active || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (this.plugin.compatibilityManager().hasPermission(event.getUniqueId(), BYPASS_PERMISSION)) return;
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.plugin.translationManager().render(MessageConstants.MAINTENANCE_KICK));
    }

    private void refreshPlayers() {
        this.clearBossBars();
        for (BukkitSparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            this.apply(player);
        }
    }

    private synchronized void apply(@NotNull BukkitSparrowPlayer player) {
        if (!this.active) return;
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            player.kick(false, this.plugin.translationManager().render(MessageConstants.MAINTENANCE_KICK, player.locale(), player.platformPlayer));
            return;
        }
        MaintenanceSettings.BossBarOptions options = this.config.bossBar();
        if (!options.enabled() || !this.bossBarViewers.add(player.uniqueId())) return;
        Component title = this.plugin.translationManager().render(MessageConstants.MAINTENANCE_BOSS_BAR, player.locale(), player.platformPlayer);
        player.showBossBar(this.bossBarId, title, 1.0f, options.getColor(), options.getOverlay());
    }

    @NotNull
    private synchronized CompletableFuture<Void> onQuit(@NotNull BukkitSparrowPlayer player) {
        this.bossBarViewers.remove(player.uniqueId());
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onDisable() {
        this.active = false;
        if (this.preLoginSubscription != null) this.preLoginSubscription.close();
        if (this.joinSubscription != null) this.joinSubscription.close();
        if (this.quitSubscription != null) this.quitSubscription.close();
        this.preLoginSubscription = null;
        this.joinSubscription = null;
        this.quitSubscription = null;
        this.clearBossBars();
        return CompletableFuture.completedFuture(null);
    }

    private void clearBossBars() {
        for (BukkitSparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            if (this.bossBarViewers.contains(player.uniqueId())) {
                player.hideBossBar(this.bossBarId);
            }
        }
        this.bossBarViewers.clear();
    }
}
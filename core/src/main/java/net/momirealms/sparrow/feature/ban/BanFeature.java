package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.FeaturePermission;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.proxy.bukkit.event.player.AsyncPlayerPreLoginEventProxy;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.IpRange;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BanFeature implements Feature {
    public static final String ID = "ban";
    public static final FeaturePermission COMMAND_PERMISSION = new FeaturePermission(ID);
    public static final String NOTIFY_PERMISSION = DependencyVersions.PROJECT_ID + ".notify.ban";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private volatile BanConfig config;
    private BanService service;
    private boolean enabled;
    private Subscription preLoginSubscription;

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

    private BanConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/ban.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(BanSettings.class, BanSettings::new)
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
        this.config = this.readConfig();
        if (this.service == null) {
            this.service = new BanService(this);
        }
        this.service.start();
        this.enabled = true;
        this.preLoginSubscription = this.plugin.playerManager().registerPreLoginListener(this::onPreLogin);
        CompletableFuture<?>[] checking = this.plugin.playerManager().getOnlinePlayers().stream()
                .map(this::checkOnline)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(checking)
                .exceptionallyCompose(error -> this.onDisable().thenCompose(ignored -> CompletableFuture.failedFuture(error)));
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    private void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        BanRecord record = this.findActive(event.getUniqueId(), event.getAddress()).join().orElse(null);
        if (record == null) return;
        Component screen = this.plugin.translationManager().render(BanTexts.kickScreen(record, event.getName(), System.currentTimeMillis()));
        AsyncPlayerPreLoginEventProxy.INSTANCE.disallow(event, AsyncPlayerPreLoginEvent.Result.KICK_BANNED, AdventureHelper.toNativeComponent(screen));
    }

    private CompletableFuture<Void> checkOnline(BukkitSparrowPlayer player) {
        return this.findActive(player.uniqueId(), player.connection.ip()).thenAccept(found -> this.apply(player, found.orElse(null)));
    }

    private synchronized CompletableFuture<Optional<BanRecord>> findActive(UUID player, @Nullable InetAddress address) {
        if (!this.enabled) return CompletableFuture.completedFuture(Optional.empty());
        return this.service.findActive(player, address == null ? IpRange.NONE : IpRange.address(address));
    }

    private synchronized void apply(BukkitSparrowPlayer player, @Nullable BanRecord record) {
        if (!this.enabled || record == null) return;
        long now = System.currentTimeMillis();
        if (record.active(now)) {
            this.kick(player, record, now);
        }
    }

    synchronized void accept(BanRecord record, boolean silent) {
        if (!this.enabled) return;
        long now = System.currentTimeMillis();
        if (record.active(now)) {
            this.kickTargets(record, now);
        }
        if (!silent) {
            this.notifyPlayers(record, now);
        }
    }

    private void kickTargets(BanRecord record, long now) {
        if (record.player() != null) {
            BukkitSparrowPlayer player = this.plugin.playerManager().getOnlinePlayer(record.player());
            if (player != null) {
                this.kick(player, record, now);
            }
            return;
        }
        for (BukkitSparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            InetAddress address = player.connection.ip();
            if (address != null && record.ip().contains(IpRange.address(address))) {
                this.kick(player, record, now);
            }
        }
    }

    private void kick(BukkitSparrowPlayer player, BanRecord record, long now) {
        if (!player.isOnlineLocally()) return;
        Component screen = this.plugin.translationManager().render(BanTexts.kickScreen(record, player.name(), now), player.locale(), player.platformPlayer);
        player.kick(true, screen);
    }

    private void notifyPlayers(BanRecord record, long now) {
        TranslatableComponent notification = BanTexts.notification(record, now);
        Sound sound = this.config.getNotifySound(record.revokedAt() == 0);
        for (BukkitSparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            if (!player.hasPermission(NOTIFY_PERMISSION)) continue;
            player.sendTranslatedMessage(notification);
            if (sound != null) {
                player.playSound(sound);
            }
        }
    }

    @NotNull
    public BanService service() {
        return this.service;
    }

    @NotNull
    public List<String> getReasonNames() {
        return this.config.reasonPresets().keySet().stream().sorted().toList();
    }

    BanConfig config() {
        return this.config;
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onDisable() {
        this.enabled = false;
        if (this.preLoginSubscription != null) this.preLoginSubscription.close();
        this.preLoginSubscription = null;
        return this.service == null ? CompletableFuture.completedFuture(null) : this.service.stop();
    }
}
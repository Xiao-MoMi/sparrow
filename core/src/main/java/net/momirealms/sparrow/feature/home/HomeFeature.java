package net.momirealms.sparrow.feature.home;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.home.placeholder.HomeExpansion;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.FeaturePermission;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.service.countdown.CountdownCancelledException;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.TeleportSubmission;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.teleport.processor.post.PlaySound;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class HomeFeature implements Feature {
    public static final String ID = "home";
    public static final FeaturePermission COMMAND_PERMISSION = new FeaturePermission(ID);

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Set<TeleportSubmission> submissions = new HashSet<>();
    private volatile HomeConfig config;
    private HomeData data;
    private HomeService service;
    private Subscription prepareSubscription;
    private Subscription joinSubscription;
    private Runnable unregisterPlaceholders;
    private long generation;

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

    private HomeConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/home.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(HomeSettings.class, HomeSettings::new)
                    .load(path)
                    .value()
                    .toConfig();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onEnable() {
        this.config = this.readConfig();
        if (this.service == null) {
            this.data = new HomeData();
            this.service = new HomeService(this.data, () -> this.config);
            this.prepareSubscription = this.plugin.playerManager().registerPrepareListener(this.service);
            this.joinSubscription = this.plugin.playerManager().registerJoinListener(this.service);
        }
        this.service.start();
        CompletableFuture<?>[] loading = this.plugin.playerManager().getOnlinePlayers().stream()
                .map(player -> this.service.preload(player.uniqueId()))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(loading)
                .thenRun(() -> {
                    if (this.plugin.compatibilityManager().hasPlaceholderAPI()) {
                        HomeExpansion expansion = new HomeExpansion();
                        expansion.register();
                        this.unregisterPlaceholders = expansion::unregister;
                    }
                })
                .exceptionallyCompose(error -> this.service.stop().thenCompose(ignored -> CompletableFuture.failedFuture(error)));
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    public HomeData data() {
        return this.data;
    }

    @NotNull
    public HomeService service() {
        return this.service;
    }

    @NotNull
    public String getDefaultName() {
        return this.config.defaultName();
    }

    @NotNull
    public synchronized CompletableFuture<@Nullable Component> teleport(@NotNull BukkitSparrowPlayer player, @NotNull Home home) {
        if (!this.plugin.featureManager().isEnabled(ID)) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_DISABLED);
        HomeConfig.Options options = this.config.teleportOptions().select(player.platformPlayer);
        long generation = this.generation;
        CompletableFuture<Duration> remaining = options.cooldown().isZero()
                ? CompletableFuture.completedFuture(Duration.ZERO)
                : this.plugin.cooldownService().remaining(player.uniqueId(), ID);
        return remaining.thenCompose(cooldown -> {
            if (!cooldown.isZero()) {
                return CompletableFuture.completedFuture(MessageConstants.TELEPORT_COOLDOWN.arguments(Component.text((cooldown.toMillis() + 999) / 1000)));
            }
            return this.commit(player, home, options, generation);
        });
    }

    private synchronized CompletableFuture<@Nullable Component> commit(BukkitSparrowPlayer player, Home home, HomeConfig.Options options, long generation) {
        if (this.generation != generation || !this.plugin.featureManager().isEnabled(ID)) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_CANCELLED);
        if (!player.isOnlineLocally()) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_UNAVAILABLE);
        if (!home.server().equals(ServerConfig.serverId()) && !this.plugin.serverManager().isOnline(home.server())) {
            return CompletableFuture.completedFuture(MessageConstants.TELEPORT_REJECTED_SERVER_OFFLINE);
        }
        Teleport teleport = Teleport.builder()
                .server(home.server())
                .position(TeleportPosition.ofPos(home.location()))
                .postProcessor(new PlaySound(options.afterSound()));
        if (!options.invulnerability().isZero()) {
            teleport.postProcessor(new Invulnerability((int) options.invulnerability().toSeconds()));
        }
        if (!options.warmup().duration().isZero()) {
            teleport.condition(this.plugin.countdownService().start(player, options.warmup(), MessageConstants.TELEPORT_WARMUP));
        }
        TeleportSubmission submission = teleport.commit(player);
        this.submissions.add(submission);
        submission.result().whenComplete((result, error) -> this.remove(submission));
        return submission.result().thenCompose(result -> switch (result) {
            case TeleportResult.Success ignored -> this.plugin.cooldownService().start(player.uniqueId(), ID, options.cooldown()).thenApply(nothing -> null);
            case TeleportResult.Fail fail -> CompletableFuture.completedFuture(fail.reason());
            case TeleportResult.Aborted aborted -> CompletableFuture.completedFuture(this.abortReason(aborted.cause()));
        });
    }

    private synchronized void remove(TeleportSubmission submission) {
        this.submissions.remove(submission);
    }

    private Component abortReason(Throwable cause) {
        if (cause instanceof CountdownCancelledException cancelled) {
            return switch (cancelled.reason()) {
                case MOVED -> MessageConstants.TELEPORT_CANCELLED_MOVED;
                case DAMAGED -> MessageConstants.TELEPORT_CANCELLED_DAMAGED;
                case REPLACED, QUIT -> MessageConstants.TELEPORT_CANCELLED;
            };
        }
        return MessageConstants.TELEPORT_CANCELLED;
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onDisable() {
        if (this.unregisterPlaceholders != null) {
            this.unregisterPlaceholders.run();
            this.unregisterPlaceholders = null;
        }
        this.generation++;
        for (TeleportSubmission submission : List.copyOf(this.submissions)) {
            submission.cancel();
        }
        return this.service == null ? CompletableFuture.completedFuture(null) : this.service.stop();
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onPluginShutdown() {
        if (this.service == null) return CompletableFuture.completedFuture(null);
        this.prepareSubscription.close();
        this.joinSubscription.close();
        return this.data.closeAsync();
    }
}
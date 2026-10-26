package net.momirealms.sparrow.feature.tpa;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.service.countdown.CountdownCancelledException;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.TeleportSubmission;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.teleport.processor.post.PlaySound;
import net.momirealms.sparrow.util.ThrowableUtils;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TpaFeature implements Feature {
    public static final String ID = "tpa";
    public static final String BYPASS_COOLDOWN_PERMISSION = DependencyVersions.PROJECT_ID + ".bypass.tpa_cooldown";
    private static final String TELEPORT_COOLDOWN = "tpa_teleport";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Set<TeleportSubmission> submissions = new HashSet<>();
    private volatile TpaConfig config;
    private volatile boolean enabled;
    private volatile long generation;
    private TpaService service;

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

    private TpaConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/tpa.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(TpaSettings.class, TpaSettings::new)
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
        if (this.service == null) this.service = new TpaService(this);
        this.enabled = true;
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    public TpaService service() {
        return this.service;
    }

    public boolean enabled() {
        return this.enabled;
    }

    TpaConfig config() {
        return this.config;
    }

    long generation() {
        return this.generation;
    }

    boolean active(long generation) {
        return this.enabled && this.generation == generation;
    }

    synchronized void teleport(BukkitSparrowPlayer player, UUID destination, String destinationName, String server) {
        if (!this.enabled || !player.isOnlineLocally()) return;
        TpaConfig.Options options = this.config.teleportOptions().select(player.platformPlayer);
        long generation = this.generation;
        CompletableFuture<Duration> remaining = options.cooldown().isZero()
                ? CompletableFuture.completedFuture(Duration.ZERO)
                : this.plugin.cooldownService().remaining(player.uniqueId(), TELEPORT_COOLDOWN);
        remaining.thenCompose(cooldown -> {
            if (!cooldown.isZero()) {
                player.sendTranslatedMessage(MessageConstants.TELEPORT_COOLDOWN.arguments(Component.text((cooldown.toMillis() + 999) / 1000)));
                return CompletableFuture.completedFuture(null);
            }
            return this.commit(player, destination, destinationName, server, options, generation);
        }).exceptionally(error -> {
            this.plugin.logger().warn("TPA teleport failed for " + player.name(), ThrowableUtils.unwrap(error));
            player.sendTranslatedMessage(MessageConstants.TELEPORT_FAILED);
            return null;
        });
    }

    private synchronized CompletableFuture<Void> commit(
            BukkitSparrowPlayer player,
            UUID destination,
            String destinationName,
            String server,
            TpaConfig.Options options,
            long generation
    ) {
        if (!this.active(generation) || !player.isOnlineLocally()) return CompletableFuture.completedFuture(null);
        if (!server.equals(ServerConfig.serverId()) && !this.plugin.serverManager().isOnline(server)) {
            return this.notify(player, MessageConstants.TELEPORT_REJECTED_SERVER_OFFLINE);
        }
        Teleport teleport = Teleport.builder()
                .server(server)
                .position(TeleportPosition.ofPlayer(destination))
                .postProcessor(new PlaySound(options.afterSound()));
        if (!options.invulnerability().isZero()) teleport.postProcessor(new Invulnerability((int) options.invulnerability().toSeconds()));
        if (!options.warmup().duration().isZero()) {
            teleport.condition(this.plugin.countdownService().start(player, options.warmup(), MessageConstants.TELEPORT_WARMUP));
        }
        TeleportSubmission submission = teleport.commit(player);
        this.submissions.add(submission);
        submission.result().whenComplete((result, error) -> this.remove(submission));
        return submission.result().thenCompose(result -> switch (result) {
            case TeleportResult.Success ignored -> this.plugin.cooldownService()
                    .start(player.uniqueId(), TELEPORT_COOLDOWN, options.cooldown())
                    .thenRun(() -> player.sendTranslatedMessage(MessageConstants.COMMAND_TPA_TELEPORT_SUCCESS.arguments(Component.text(destinationName))));
            case TeleportResult.Fail fail -> this.notify(player, fail.reason());
            case TeleportResult.Aborted aborted -> this.notify(player, this.abortReason(aborted.cause()));
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

    private CompletableFuture<Void> notify(BukkitSparrowPlayer player, Component reason) {
        player.sendTranslatedMessage(reason);
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onDisable() {
        this.enabled = false;
        this.generation++;
        if (this.service != null) this.service.stop();
        for (TeleportSubmission submission : List.copyOf(this.submissions)) {
            submission.cancel();
        }
        return CompletableFuture.completedFuture(null);
    }
}
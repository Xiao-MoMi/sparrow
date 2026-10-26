package net.momirealms.sparrow.feature.spawn;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.service.countdown.CountdownCancelledException;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.TeleportSubmission;
import net.momirealms.sparrow.util.ServerLocation;
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
import java.util.concurrent.CompletableFuture;

public final class SpawnFeature implements Feature {
    public static final String ID = "spawn";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Set<TeleportSubmission> submissions = new HashSet<>();
    private SpawnConfig config;
    private boolean enabled;
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

    @NotNull
    private SpawnConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/spawn.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(SpawnSettings.class, SpawnSettings::new)
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
        this.enabled = true;
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    public synchronized CompletableFuture<Void> teleport(@NotNull BukkitSparrowPlayer player) {
        if (!this.enabled) return this.notify(player, MessageConstants.TELEPORT_DISABLED);
        ServerLocation destination = this.config.destination();
        if (destination == null) return this.notify(player, MessageConstants.COMMAND_SPAWN_NOT_SET);
        SpawnConfig.Options options = this.config.select(player.platformPlayer);
        long generation = this.generation;
        CompletableFuture<Duration> remaining = options.cooldown().isZero()
                ? CompletableFuture.completedFuture(Duration.ZERO)
                : this.plugin.cooldownService().remaining(player.uniqueId(), ID);
        return remaining.thenCompose(cooldown -> {
            if (!cooldown.isZero()) {
                Component seconds = Component.text((cooldown.toMillis() + 999) / 1000);
                return this.notify(player, MessageConstants.TELEPORT_COOLDOWN.arguments(seconds));
            }
            return this.commit(player, destination, options, generation);
        }).exceptionally(error -> {
            this.plugin.logger().warn("Failed to spawn for " + player.name(), ThrowableUtils.unwrap(error));
            player.sendTranslatedMessage(MessageConstants.TELEPORT_FAILED);
            return null;
        });
    }

    private synchronized CompletableFuture<Void> commit(BukkitSparrowPlayer player, ServerLocation destination, SpawnConfig.Options options, long generation) {
        if (!this.enabled || this.generation != generation) return this.notify(player, MessageConstants.TELEPORT_CANCELLED);
        if (!player.isOnlineLocally()) return CompletableFuture.completedFuture(null);
        if (!destination.server().equals(ServerConfig.serverId()) && !this.plugin.serverManager().isOnline(destination.server())) {
            return this.notify(player, MessageConstants.TELEPORT_REJECTED_SERVER_OFFLINE);
        }
        Teleport teleport = Teleport.builder()
                .server(destination.server())
                .position(destination.world(), destination.x(), destination.y(), destination.z(), destination.yaw(), destination.pitch());
        if (!options.warmup().duration().isZero()) {
            teleport.condition(this.plugin.countdownService().start(player, options.warmup(), MessageConstants.TELEPORT_WARMUP));
        }
        TeleportSubmission submission = teleport.commit(player);
        this.submissions.add(submission);
        submission.result().whenComplete((result, error) -> this.remove(submission));
        return submission.result().thenCompose(result -> switch (result) {
            case TeleportResult.Success ignored -> this.plugin.cooldownService()
                    .start(player.uniqueId(), ID, options.cooldown())
                    .thenRun(() -> player.sendTranslatedMessage(MessageConstants.COMMAND_SPAWN_SUCCESS));
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

    private CompletableFuture<Void> notify(BukkitSparrowPlayer player, Component message) {
        player.sendTranslatedMessage(message);
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onDisable() {
        this.enabled = false;
        this.generation++;
        for (TeleportSubmission submission : List.copyOf(this.submissions)) {
            submission.cancel();
        }
        return CompletableFuture.completedFuture(null);
    }
}
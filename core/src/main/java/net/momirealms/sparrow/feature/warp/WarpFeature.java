package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.database.WarpStore;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.service.countdown.CountdownCancelledException;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.TeleportSubmission;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.teleport.processor.post.PlaySound;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.command.CommandSender;
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

public final class WarpFeature implements Feature {
    public static final String ID = "warp";
    private static final String USE_PERMISSION = DependencyVersions.PROJECT_ID + ".warp.use.";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Set<TeleportSubmission> submissions = new HashSet<>();
    private volatile WarpConfig config;
    private volatile WarpRegistry registry;
    private WarpService service;
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

    private WarpConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/warp.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(WarpSettings.class, WarpSettings::new)
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
        WarpConfig loaded = this.readConfig();
        WarpStore store = this.plugin.dataStorage().warpStore();
        WarpRegistry registry = new WarpRegistry(store);
        this.config = loaded;
        this.registry = registry;
        this.service = new WarpService(this, store, registry);
        return registry.load().whenComplete((ignored, error) -> {
            if (error != null) registry.close();
        });
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> onDisable() {
        this.generation++;
        for (TeleportSubmission submission : List.copyOf(this.submissions)) {
            submission.cancel();
        }
        WarpRegistry registry = this.registry;
        if (registry != null) registry.close();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    public WarpRegistry registry() {
        return this.registry;
    }

    @NotNull
    public WarpService service() {
        return this.service;
    }

    WarpConfig config() {
        return this.config;
    }

    public boolean visible(@NotNull CommandSender sender, @NotNull Warp warp) {
        return !this.config.permissionRestrict() || sender.hasPermission(USE_PERMISSION + warp.getNameKey());
    }

    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String input) {
        return this.registry.complete(input, warp -> this.visible(sender, warp));
    }

    @NotNull
    synchronized CompletableFuture<@Nullable Component> teleport(BukkitSparrowPlayer player, Warp warp) {
        if (!this.plugin.featureManager().isEnabled(ID)) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_DISABLED);
        WarpConfig.Options options = this.config.teleportOptions().select(player.platformPlayer);
        long generation = this.generation;
        CompletableFuture<Duration> remaining = options.cooldown().isZero()
                ? CompletableFuture.completedFuture(Duration.ZERO)
                : this.plugin.cooldownService().remaining(player.uniqueId(), ID);
        return remaining.thenCompose(cooldown -> {
            if (!cooldown.isZero()) {
                return CompletableFuture.completedFuture(MessageConstants.TELEPORT_COOLDOWN.arguments(Component.text((cooldown.toMillis() + 999) / 1000)));
            }
            return this.commit(player, warp, options, generation);
        });
    }

    private synchronized CompletableFuture<@Nullable Component> commit(BukkitSparrowPlayer player, Warp warp, WarpConfig.Options options, long generation) {
        if (this.generation != generation || !this.plugin.featureManager().isEnabled(ID)) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_CANCELLED);
        if (!player.isOnlineLocally()) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_UNAVAILABLE);
        if (!warp.server().equals(ServerConfig.serverId()) && !this.plugin.serverManager().isOnline(warp.server())) {
            return CompletableFuture.completedFuture(MessageConstants.TELEPORT_REJECTED_SERVER_OFFLINE);
        }
        Teleport teleport = Teleport.builder()
                .server(warp.server())
                .position(TeleportPosition.ofPos(warp.location()))
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

    void accept(WarpMessage message) {
        WarpRegistry registry = this.registry;
        if (registry == null || message.origin().equals(ServerConfig.serverId())) return;
        registry.refresh(message.warpId()).whenComplete((ignored, error) -> {
            if (error != null) this.plugin.logger().warn("Failed to refresh warp " + message.warpId(), error);
        });
    }
}
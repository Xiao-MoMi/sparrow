package net.momirealms.sparrow.feature.back;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.BackStore;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.service.countdown.CountdownCancelledException;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.teleport.processor.post.PlaySound;
import net.momirealms.sparrow.teleport.processor.target.safe.FindSafeLoc;
import net.momirealms.sparrow.teleport.search.ChunkPolicy;
import net.momirealms.sparrow.teleport.search.PhysicalSafety;
import net.momirealms.sparrow.teleport.search.SearchPattern;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.util.ThrowableUtils;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

final class BackService {
    private static final String COOLDOWN_ID = "back";
    private static final FindSafeLoc SAFE_LOCATION = FindSafeLoc.builder()
            .search(SearchPattern.exact(), SearchPattern.nearby().horizontalRadius(4).up(8).down(8).build())
            .rules(PhysicalSafety.builder().build())
            .chunks(ChunkPolicy.ALLOW_GENERATION)
            .maxChecks(10_000)
            .timeout(Duration.ofSeconds(9))
            .build();

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final BackFeature feature;
    private final BackStore store;
    private final BackOperations operations;

    BackService(BackFeature feature, BackStore store, BackOperations operations) {
        this.feature = feature;
        this.store = store;
        this.operations = operations;
    }

    CompletableFuture<Void> record(BukkitSparrowPlayer player, ServerLocation location) {
        return this.operations.submit(player.uniqueId(), () -> this.store.saveBack(player.uniqueId(), location));
    }

    CompletableFuture<@Nullable Component> teleport(BukkitSparrowPlayer player, BackConfig.Options options, long generation, boolean unsafe) {
        CompletableFuture<Duration> remaining = options.cooldown().isZero()
                ? CompletableFuture.completedFuture(Duration.ZERO)
                : this.plugin.cooldownService().remaining(player.uniqueId(), COOLDOWN_ID);
        return remaining.thenCompose(cooldown -> this.feature.withPlayer(player, generation, () -> {
            if (!cooldown.isZero()) {
                Component seconds = Component.text((cooldown.toMillis() + 999) / 1000);
                return CompletableFuture.completedFuture(this.notify(player, MessageConstants.TELEPORT_COOLDOWN.arguments(seconds)));
            }
            return this.feature.withPlayer(player, generation, () -> this.operations.submit(player.uniqueId(), () -> this.store.loadBack(player.uniqueId())))
                    .thenCompose(found -> this.feature.withPlayer(player, generation, () -> {
                        if (found.isEmpty()) return CompletableFuture.completedFuture(this.notify(player, MessageConstants.COMMAND_BACK_NO_LOCATION));
                        ServerLocation destination = found.get();
                        if (!destination.server().equals(ServerConfig.serverId()) && !this.plugin.serverManager().isOnline(destination.server())) {
                            return CompletableFuture.completedFuture(this.notify(player, MessageConstants.TELEPORT_REJECTED_SERVER_OFFLINE));
                        }
                        return this.commit(player, options, destination, unsafe);
                    }));
        })).exceptionally(error -> {
            Throwable cause = ThrowableUtils.unwrap(error);
            if (cause instanceof CancellationException) {
                if (this.plugin.playerManager().getOnlinePlayer(player.platformPlayer) != player) return MessageConstants.TELEPORT_CANCELLED;
                return this.notify(player, MessageConstants.TELEPORT_CANCELLED);
            }
            this.plugin.logger().warn("Failed to back for " + player.name(), cause);
            return this.notify(player, MessageConstants.TELEPORT_FAILED);
        });
    }

    private CompletableFuture<@Nullable Component> commit(BukkitSparrowPlayer player, BackConfig.Options options, ServerLocation destination, boolean unsafe) {
        TeleportContext context = new TeleportContext();
        context.toNbt().putBoolean(TeleportContext.UNSAFE, unsafe);
        Teleport teleport = Teleport.builder()
                .context(context)
                .server(destination.server())
                .position(destination.world(), destination.x(), destination.y(), destination.z(), destination.yaw(), destination.pitch())
                .targetProcessor(SAFE_LOCATION)
                .postProcessor(new PlaySound(options.afterSound()));
        if (!options.invulnerability().isZero()) {
            teleport.postProcessor(new Invulnerability((int) options.invulnerability().toSeconds()));
        }
        if (!options.warmup().duration().isZero()) {
            teleport.condition(this.plugin.countdownService().start(player, options.warmup(), MessageConstants.TELEPORT_WARMUP));
        }
        return teleport.commit(player).result().thenCompose(result -> switch (result) {
            case TeleportResult.Success ignored -> this.plugin.cooldownService().start(player.uniqueId(), COOLDOWN_ID, options.cooldown()).thenApply(nothing -> null);
            case TeleportResult.Fail fail -> CompletableFuture.completedFuture(this.notify(player, fail.reason()));
            case TeleportResult.Aborted aborted -> CompletableFuture.completedFuture(this.aborted(player, aborted.cause()));
        });
    }

    private Component aborted(BukkitSparrowPlayer player, Throwable cause) {
        if (cause instanceof CountdownCancelledException cancelled) {
            return switch (cancelled.reason()) {
                case MOVED -> this.notify(player, MessageConstants.TELEPORT_CANCELLED_MOVED);
                case DAMAGED -> this.notify(player, MessageConstants.TELEPORT_CANCELLED_DAMAGED);
                case REPLACED, QUIT -> MessageConstants.TELEPORT_CANCELLED;
            };
        }
        return this.notify(player, MessageConstants.TELEPORT_CANCELLED);
    }

    private Component notify(BukkitSparrowPlayer player, Component reason) {
        player.sendTranslatedMessage(reason);
        return reason;
    }
}
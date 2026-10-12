package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerLevel;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportDestination.Result;
import net.momirealms.sparrow.teleport.rule.CandidateChecks;
import net.momirealms.sparrow.teleport.rule.CandidateContext;
import net.momirealms.sparrow.teleport.rule.RequestContext;
import net.momirealms.sparrow.teleport.rule.SafeLocationRule;
import net.momirealms.sparrow.util.TeleportSafety;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class DestinationContext {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Executor executor = VersionHelper.hasPaperPatch ? this.plugin.scheduler().async() : this.plugin.scheduler().platform();
    private final UUID player;
    private final boolean self;
    private final @Nullable TeleportDimensions dimensions;
    private final TeleportGroup group;
    private final CompletableFuture<Result> completion = new CompletableFuture<>();

    public DestinationContext(@NotNull Teleport teleport, @NotNull TeleportGroup group) {
        this.player = teleport.player();
        this.self = teleport.self();
        this.dimensions = teleport.type() == null ? null : teleport.dimensions();
        this.group = group;
    }

    // 原始位置只用于准入和选点, 返回结果中的坐标已经通过所有适用规则
    CompletableFuture<Result> resolvePoint(WorldLocation origin) {
        CompletableFuture<Result> resolving = CompletableFuture.completedFuture(origin)
                .thenCompose(point -> {
                    Location location = point.resolve();
                    if (location == null) return CompletableFuture.completedFuture(new Result(null, null));
                    if (this.group.search() instanceof TeleportSearchOptions.Direct && this.group.requestRule().isEmpty() && this.group.candidateRule().isEmpty()) {
                        return CompletableFuture.completedFuture(new Result(point, null));
                    }
                    return this.plugin.playerLookup().resolvePlayer(this.player)
                            .thenCompose(found -> {
                                SparrowPlayer player = found.orElseThrow();
                                RequestContext request = new RequestContext(player, this.self, point.world(), point);
                                return this.checkRequest(request).thenCompose(reason -> {
                                    if (reason != null) return CompletableFuture.completedFuture(new Result(null, reason));
                                    return this.track(() -> switch (this.group.search()) {
                                        case TeleportSearchOptions.Direct ignored -> this.checkPoint(player, location, point, false);
                                        case TeleportSearchOptions.Nearby options -> this.searchNearby(player, location, point, options);
                                    });
                                });
                            });
                });
        resolving.whenComplete((result, error) -> {
            if (error != null) {
                this.completion.completeExceptionally(error);
            } else {
                this.completion.complete(result);
            }
        });
        return this.completion;
    }

    private CompletableFuture<Component> checkRequest(RequestContext context) {
        CompletableFuture<Component> checked = TeleportProcessor.PASS;
        for (TeleportProcessor.RequestRule rule : this.group.requestRule()) {
            checked = checked.thenCompose(reason -> {
                if (reason != null) return CompletableFuture.completedFuture(reason);
                return this.track(() -> rule.check(context));
            });
        }
        return checked;
    }

    private CompletableFuture<Result> searchNearby(SparrowPlayer player, Location location, WorldLocation origin, TeleportSearchOptions.Nearby options) {
        ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();
        TeleportSearch search = new TeleportSearch(world, player, this.dimensions, options.allowNetherRoof(), options.maxChecks(), this.group.candidateRule());
        return this.track(() -> search.find(origin, options.horizontalRadius(), options.verticalRadius()))
                .thenCompose(found -> {
                    if (found != null) return CompletableFuture.completedFuture(new Result(found, null));
                    if (!options.forceOnFailure()) return CompletableFuture.completedFuture(new Result(null, MessageConstants.TELEPORT_REJECTED_UNSAFE));
                    double y = Math.max(origin.y(), Math.max(options.fallbackMinY(), world.getMinY() + 1));
                    WorldLocation fallback = new WorldLocation(origin.world(), origin.x(), y, origin.z(), origin.yaw(), origin.pitch());
                    return this.track(() -> this.checkPoint(player, location, fallback, true));
                });
    }

    private CompletableFuture<Result> checkPoint(SparrowPlayer player, Location location, WorldLocation candidate, boolean fallback) {
        if (this.group.candidateRule().isEmpty()) return CompletableFuture.completedFuture(new Result(candidate, null));
        ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();
        SearchChunks chunks = new SearchChunks(world, false);
        CandidateContext context = new CandidateContext(player, this.dimensions, chunks, TeleportSafety.getMaxY(world, false));
        CandidateChecks checks = new CandidateChecks(context, this.group.candidateRule(), this.completion);
        // 内置安全规则读取已准备区块, 其他规则只使用玩家信息和候选坐标
        boolean prepareChunks = !fallback && this.group.candidateRule().stream().anyMatch(SafeLocationRule.class::isInstance);
        CompletableFuture<Boolean> ready = prepareChunks
                ? chunks.prepare(this.dimensions, candidate.x(), candidate.z())
                : CompletableFuture.completedFuture(true);
        return ready.thenComposeAsync(available -> {
            if (!available) return CompletableFuture.completedFuture(new Result(null, MessageConstants.TELEPORT_REJECTED_UNSAFE));
            return this.track(() -> fallback ? checks.checkFallback(candidate) : checks.check(candidate))
                    .thenApply(reason -> new Result(reason == null ? candidate : null, reason));
        }, this.executor);
    }

    private <T> CompletableFuture<T> track(Supplier<CompletableFuture<T>> action) {
        if (this.completion.isDone()) return CompletableFuture.failedFuture(new CancellationException());
        CompletableFuture<T> future = action.get();
        this.completion.whenComplete((result, error) -> {
            if (error != null) future.cancel(false);
        });
        return future;
    }
}
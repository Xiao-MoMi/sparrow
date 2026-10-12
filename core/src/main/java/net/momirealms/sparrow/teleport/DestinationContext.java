package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerLevel;
import net.momirealms.sparrow.feature.rtp.RtpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportDestination.Result;
import net.momirealms.sparrow.teleport.processor.CandidateChecks;
import net.momirealms.sparrow.teleport.processor.CandidateContext;
import net.momirealms.sparrow.teleport.processor.RequestContext;
import net.momirealms.sparrow.teleport.processor.SafeLocationRule;
import net.momirealms.sparrow.teleport.processor.TeleportProcessor;
import net.momirealms.sparrow.teleport.search.NearbyTeleportSearch;
import net.momirealms.sparrow.teleport.search.RandomTeleportArea;
import net.momirealms.sparrow.teleport.search.RandomTeleportSearch;
import net.momirealms.sparrow.teleport.search.SearchChunks;
import net.momirealms.sparrow.teleport.search.TeleportSearchOptions;
import net.momirealms.sparrow.util.TeleportSafety;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
                    if (this.group.search() instanceof TeleportSearchOptions.Random) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("random search requires a random area destination"));
                    }
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
                                        case TeleportSearchOptions.Random ignored -> throw new IllegalArgumentException("random search requires a random area destination");
                                    });
                                });
                            });
                });
        resolving.whenComplete(this::complete);
        return this.completion;
    }

    CompletableFuture<Result> resolveRandom(RandomTeleportArea area) {
        CompletableFuture<Result> resolving = CompletableFuture.completedFuture(area)
                .thenCompose(input -> {
                    if (!VersionHelper.hasPaperPatch) return CompletableFuture.completedFuture(new Result(null, MessageConstants.TELEPORT_REJECTED_RTP_UNSUPPORTED));
                    if (!this.plugin.featureManager().feature(RtpFeature.ID, RtpFeature.class).enabled()) {
                        return CompletableFuture.completedFuture(new Result(null, MessageConstants.TELEPORT_REJECTED_RTP_DISABLED));
                    }
                    input.validate();
                    World world = Bukkit.getWorld(input.world());
                    if (world == null) return CompletableFuture.completedFuture(new Result(null, null));
                    if (!(this.group.search() instanceof TeleportSearchOptions.Random options)) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("RTP destination requires the random search strategy"));
                    }
                    return this.plugin.playerLookup().resolvePlayer(this.player)
                            .thenCompose(found -> {
                                SparrowPlayer player = found.orElseThrow();
                                RequestContext request = new RequestContext(player, this.self, world.getName(), null);
                                return this.checkRequest(request)
                                        .thenCompose(reason -> {
                                            if (reason != null) return CompletableFuture.completedFuture(new Result(null, reason));
                                            return this.track(() -> {
                                                ServerLevel level = ((CraftWorld) world).getHandle();
                                                RandomTeleportSearch search = new RandomTeleportSearch(level, player, this.dimensions, options, this.group.candidateRule());
                                                return search.find(input);
                                            })
                                                    .thenApply(location -> new Result(location, location == null ? MessageConstants.TELEPORT_REJECTED_UNSAFE : null));
                                        });
                            });
                });
        resolving.whenComplete(this::complete);
        return this.completion;
    }

    private void complete(Result result, Throwable error) {
        if (error != null) {
            this.completion.completeExceptionally(error);
        } else {
            this.completion.complete(result);
        }
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
        NearbyTeleportSearch search = new NearbyTeleportSearch(world, player, this.dimensions, options.allowNetherRoof(), options.maxChecks(), this.group.candidateRule());
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
package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerLevel;
import net.momirealms.sparrow.feature.rtp.RtpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class DestinationResolver {
    private static final int PREPARATION_TIMEOUT_SECONDS = 10;

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Executor executor = VersionHelper.hasPaperPatch ? this.plugin.scheduler().async() : this.plugin.scheduler().platform();
    private final Teleport teleport;
    private final TeleportGroup group;
    private final CompletableFuture<Result> completion = new CompletableFuture<>();

    public DestinationResolver(@NotNull Teleport teleport, @NotNull TeleportGroup group) {
        this.teleport = teleport;
        this.group = group;
    }

    // 每个解析器执行一次目标准备, 超时和取消通过 completion 传给当前规则或搜索
    @NotNull
    public CompletableFuture<Result> resolve() {
        this.completion.orTimeout(PREPARATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        CompletableFuture<Result> resolving = CompletableFuture.completedFuture(this.teleport.destination())
                .thenCompose(destination -> {
                    if (this.completion.isDone()) return CompletableFuture.failedFuture(new CancellationException());
                    return switch (destination) {
                        case TeleportDestination.Fixed fixed -> this.resolvePoint(fixed.location());
                        case TeleportDestination.OfPlayer target -> this.resolvePlayer(target);
                        case TeleportDestination.RTP random -> this.resolveRandom(random.area());
                    };
                });
        resolving.whenComplete(this::complete);
        return this.completion;
    }

    private CompletableFuture<Result> resolvePlayer(TeleportDestination.OfPlayer target) {
        BukkitSparrowPlayer player = this.plugin.playerManager().getPlayer(target.player());
        return player == null
                ? CompletableFuture.completedFuture(new Result(null, null))
                : this.resolvePoint(WorldLocation.from(player.platformPlayer().getLocation()));
    }

    // 坐标目标按分组选择直接检查或附近搜索
    private CompletableFuture<Result> resolvePoint(WorldLocation origin) {
        Location location = origin.resolve();
        if (location == null) return CompletableFuture.completedFuture(new Result(null, null));
        if (this.group.search() instanceof TeleportSearchOptions.Random) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("random search requires a random area destination"));
        }
        if (this.group.search() instanceof TeleportSearchOptions.Direct && this.group.requestRule().isEmpty() && this.group.candidateRule().isEmpty()) {
            return CompletableFuture.completedFuture(new Result(origin, null));
        }
        return this.plugin.playerLookup().resolvePlayer(this.teleport.player())
                .thenCompose(found -> {
                    SparrowPlayer player = found.orElseThrow();
                    RequestContext request = new RequestContext(player, this.teleport.self(), origin.world(), origin);
                    return this.checkRequest(request).thenCompose(reason -> {
                        if (reason != null) return CompletableFuture.completedFuture(new Result(null, reason));
                        return this.track(() -> switch (this.group.search()) {
                            case TeleportSearchOptions.Direct ignored -> this.checkPoint(player, location, origin, false);
                            case TeleportSearchOptions.Nearby options -> this.searchNearby(player, location, origin, options);
                            case TeleportSearchOptions.Random ignored -> throw new IllegalArgumentException("random search requires a random area destination");
                        });
                    });
                });
    }

    private CompletableFuture<Result> resolveRandom(RandomTeleportArea area) {
        if (!VersionHelper.hasPaperPatch) return CompletableFuture.completedFuture(new Result(null, MessageConstants.TELEPORT_REJECTED_RTP_UNSUPPORTED));
        if (!this.plugin.featureManager().feature(RtpFeature.ID, RtpFeature.class).enabled()) {
            return CompletableFuture.completedFuture(new Result(null, MessageConstants.TELEPORT_REJECTED_RTP_DISABLED));
        }
        area.validate();
        World world = Bukkit.getWorld(area.world());
        if (world == null) return CompletableFuture.completedFuture(new Result(null, null));
        if (!(this.group.search() instanceof TeleportSearchOptions.Random options)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("RTP destination requires the random search strategy"));
        }
        return this.plugin.playerLookup().resolvePlayer(this.teleport.player())
                .thenCompose(found -> {
                    SparrowPlayer player = found.orElseThrow();
                    RequestContext request = new RequestContext(player, this.teleport.self(), world.getName(), null);
                    return this.checkRequest(request)
                            .thenCompose(reason -> {
                                if (reason != null) return CompletableFuture.completedFuture(new Result(null, reason));
                                return this.track(() -> {
                                    ServerLevel level = ((CraftWorld) world).getHandle();
                                    RandomTeleportSearch search = new RandomTeleportSearch(level, player, this.teleport.dimensions(), options, this.group.candidateRule());
                                    return search.find(area);
                                })
                                        .thenApply(location -> new Result(location, location == null ? MessageConstants.TELEPORT_REJECTED_UNSAFE : null));
                            });
                });
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
        NearbyTeleportSearch search = new NearbyTeleportSearch(world, player, this.teleport.dimensions(), options.allowNetherRoof(), options.maxChecks(), this.group.candidateRule());
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
        CandidateContext context = new CandidateContext(player, this.teleport.dimensions(), chunks, TeleportSafety.getMaxY(world, false));
        CandidateChecks checks = new CandidateChecks(context, this.group.candidateRule(), this.completion);
        // 内置安全规则读取已准备区块, 其他规则只使用玩家信息和候选坐标
        boolean prepareChunks = !fallback && this.group.candidateRule().stream().anyMatch(SafeLocationRule.class::isInstance);
        CompletableFuture<Boolean> ready = prepareChunks
                ? chunks.prepare(this.teleport.dimensions(), candidate.x(), candidate.z())
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

    private void complete(Result result, Throwable error) {
        if (error != null) {
            this.completion.completeExceptionally(error);
        } else {
            this.completion.complete(result);
        }
    }

    public record Result(@Nullable WorldLocation location, @Nullable Component denial) {
    }
}
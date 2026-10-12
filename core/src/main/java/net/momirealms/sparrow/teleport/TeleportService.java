package net.momirealms.sparrow.teleport;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.back.BackFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.configuration.TeleportConfig;
import net.momirealms.sparrow.redis.message.teleport.TeleportRequest;
import net.momirealms.sparrow.redis.message.teleport.TeleportResponse;
import net.momirealms.sparrow.redis.proxy.ConnectResult;
import net.momirealms.sparrow.teleport.processor.InvulnerabilityProcessor;
import net.momirealms.sparrow.teleport.processor.TeleportProcessor;
import net.momirealms.sparrow.teleport.processor.warmup.WarmupManager;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class TeleportService implements PlayerListener {
    private static final int PREPARATION_TIMEOUT_SECONDS = 10;
    private static final TeleportGroup DIRECT = new TeleportGroup(); // 直接传送使用的空分组

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Cache<UUID, Arrival> arrivals = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build();
    private final WarmupManager warmupManager = new WarmupManager();

    public void onEnable() {
        Listener arrivalListener = VersionHelper.hasPaperPatch ? new PaperArrivalListener() : new SpigotArrivalListener();
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(arrivalListener, this.plugin.javaPlugin());
        this.plugin.playerManager().registerListener(this);
        this.warmupManager.onEnable();
    }

    /**
     * 让玩家依次经过所属传送分组的处理器, 全部放行后送到目标位置.
     *
     * @return 传送结果, 落点准备超过 10 秒或代理切服等待超过 5 秒时以 {@link TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TeleportResult> teleport(
            @NotNull Player player,
            @NotNull TeleportType type,
            @NotNull String server,
            @NotNull WorldLocation destination,
            boolean self
    ) {
        return this.teleport(player, type, server, new TeleportDestination.Fixed(destination), self);
    }

    /**
     * 让玩家依次经过所属传送分组的处理器, 全部放行后送到落点. 落点由它所在的服务器在放行后算出.
     *
     * @return 传送结果, 落点准备超过 10 秒或代理切服等待超过 5 秒时以 {@link TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TeleportResult> teleport(
            @NotNull Player player,
            @NotNull TeleportType type,
            @NotNull String server,
            @NotNull TeleportDestination destination,
            boolean self
    ) {
        BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
        if (sparrow == null) return CompletableFuture.completedFuture(TeleportResult.FAILED);
        Teleport teleport = new Teleport(player.getUniqueId(), type, server, destination, self);
        List<TeleportProcessor.Pre> processors = this.group(type).preProcessor();
        // 出发前处理器逐个执行, 前一个放行后才轮到下一个
        CompletableFuture<Component> denial = TeleportProcessor.PASS;
        for (TeleportProcessor.Pre processor : processors) {
            denial = denial.thenCompose(reason -> {
                if (reason != null) return CompletableFuture.completedFuture(reason);
                return processor.before(sparrow, teleport);
            });
        }
        return denial
                .thenCompose(reason -> {
                    if (reason != null) return CompletableFuture.completedFuture(this.reject(player, reason));
                    teleport.dimensions(TeleportDimensions.from(sparrow.nmsPlayer()));
                    return this.transfer(player, teleport);
                })
                .thenApply(result -> {
                    for (TeleportProcessor.Pre processor : processors) {
                        processor.finished(teleport, result);
                    }
                    return result;
                });
    }

    private TeleportGroup group(@Nullable TeleportType type) {
        if (type == null) return DIRECT;
        FeaturesConfig.ConfigDefinition features = this.plugin.configurationManager().featuresConfig().config();
        return TeleportConfig.group(switch (type) {
            case WARP -> features.warp().teleportGroup();
            case HOME -> features.home().teleportGroup();
            case BACK -> features.back().teleportGroup();
            case DEATH_BACK -> features.back().deathTeleportGroup();
            case BED -> features.bed().teleportGroup();
            case SPAWN -> features.spawn().teleportGroup();
            case TPA -> features.tpa().teleportGroup();
        });
    }

    /**
     * 不经过任何处理器, 直接把玩家送到指定服务器上的位置.
     *
     * @return 传送结果, 落点准备超过 10 秒或代理切服等待超过 5 秒时以 {@link TimeoutException} 异常完成
     */
    @NotNull
    public CompletableFuture<TeleportResult> transfer(@NotNull Player player, @NotNull String server, @NotNull WorldLocation location) {
        return this.transfer(player, new Teleport(player.getUniqueId(), null, server, new TeleportDestination.Fixed(location), false));
    }

    // 本服解析后传送, 跨服先在目标服完成解析并预留最终位置, 再请求代理切服
    private CompletableFuture<TeleportResult> transfer(Player player, Teleport teleport) {
        String server = teleport.server();
        if (ServerConfig.serverId().equals(server)) {
            return this.prepareDestination(teleport)
                    .thenCompose(response -> {
                        if (response.denial() != null) return CompletableFuture.completedFuture(this.reject(player, response.denial()));
                        if (response.location() == null) return CompletableFuture.completedFuture(TeleportResult.INVALID);
                        Location destination = response.location().resolve();
                        if (destination == null) return CompletableFuture.completedFuture(TeleportResult.INVALID);
                        Location from = VersionHelper.hasFoliaPatch ? player.getLocation() : null;
                        CompletableFuture<Boolean> moved = VersionHelper.hasPaperPatch
                                ? player.teleportAsync(destination, TeleportCause.PLUGIN)
                                : CompletableFuture.supplyAsync(() -> player.teleport(destination, TeleportCause.PLUGIN), this.plugin.scheduler().platform());
                        return moved.thenApply(success -> {
                            if (!success) return TeleportResult.FAILED;
                            // Folia 的异步传送通过成功回调补录返回点, 先于到达后处理器执行
                            if (from != null) {
                                BackFeature back = this.plugin.featureManager().feature(BackFeature.ID, BackFeature.class);
                                back.recordTeleport(player, from, destination, TeleportCause.PLUGIN);
                            }
                            BukkitSparrowPlayer arrived = this.plugin.playerManager().getPlayer(player);
                            if (arrived != null) this.arrived(arrived, teleport);
                            return TeleportResult.LOCAL_SUCCESS;
                        });
                    });
        }
        if (!this.plugin.serverDirectory().isOnline(server)) {
            return CompletableFuture.completedFuture(TeleportResult.SERVER_OFFLINE);
        }
        return this.plugin.messageBrokerManager().broker()
                .publishTwoWay(new TeleportRequest(teleport), server)
                .orTimeout(PREPARATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenCompose(response -> {
                    Component denial = response.denial();
                    if (denial != null) return CompletableFuture.completedFuture(this.reject(player, denial));
                    if (!response.accepted()) return CompletableFuture.completedFuture(TeleportResult.INVALID);
                    BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
                    if (sparrow == null || !player.isOnline()) return CompletableFuture.completedFuture(TeleportResult.FAILED);
                    return sparrow.connect(server)
                            .thenApply(result -> result == ConnectResult.SUCCESS ? TeleportResult.REMOTE_SUCCESS : TeleportResult.FAILED);
                });
    }

    /**
     * 在目标服解析通过规则的最终位置, 预留后等待玩家进服.
     *
     * @return 给来源服务器的回应, 被拒绝时带有给玩家的提示
     */
    @NotNull
    public CompletableFuture<TeleportResponse> prepare(@NotNull Teleport teleport) {
        return this.prepareDestination(teleport)
                .thenApply(response -> {
                    if (response.location() == null) return new TeleportResponse(false, response.denial());
                    if (response.location().resolve() == null) return new TeleportResponse(false, null);
                    this.arrivals.put(teleport.player(), new Arrival(teleport, false));
                    return new TeleportResponse(true, null);
                });
    }

    private CompletableFuture<TeleportDestination.Result> prepareDestination(Teleport teleport) {
        CompletableFuture<TeleportDestination.Result> completion = new CompletableFuture<>();
        completion.orTimeout(PREPARATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CompletableFuture<TeleportDestination.Result> preparation = CompletableFuture.completedFuture(teleport)
                .thenCompose(value -> this.runPreparationStage(completion, () -> value.locate(new DestinationContext(value, this.group(value.type())))));

        preparation.whenComplete((response, error) -> {
            if (error != null) {
                completion.completeExceptionally(error);
            } else {
                completion.complete(response);
            }
        });
        return completion;
    }

    // 准备超时后取消当前阶段, 搜索在候选循环和区块回调中响应取消
    private <T> CompletableFuture<T> runPreparationStage(CompletableFuture<?> completion, Supplier<CompletableFuture<T>> action) {
        if (completion.isDone()) return CompletableFuture.failedFuture(new CancellationException());
        CompletableFuture<T> stage = action.get();
        completion.whenComplete((response, error) -> {
            if (error != null) stage.cancel(false);
        });
        return stage;
    }

    private void arrived(BukkitSparrowPlayer player, Teleport teleport) {
        for (TeleportProcessor.Post processor : this.group(teleport.type()).postProcessor()) {
            processor.arrived(player, teleport);
        }
    }

    // 把拒绝的原因提示给被传送的玩家, 空组件表示处理器已经自行提示
    private TeleportResult reject(Player player, Component reason) {
        BukkitSparrowPlayer sparrow = this.plugin.playerManager().getPlayer(player);
        if (sparrow != null && !reason.equals(Component.empty())) sparrow.sendMessage(sparrow.render(reason));
        return TeleportResult.REJECTED;
    }

    @Nullable
    public Location getSpawnLocation(@NotNull UUID player) {
        Arrival arrival = this.arrivals.getIfPresent(player);
        if (arrival == null || arrival.invalid) return null;
        Location location = arrival.teleport.location().resolve();
        // 记录留到 Join 再处理: 配置阶段还不能发送游戏聊天, 到达后处理器也要等玩家进入世界
        this.arrivals.put(player, new Arrival(arrival.teleport, location == null));
        return location;
    }

    @Override
    public void onJoin(@NotNull BukkitSparrowPlayer player) {
        Arrival arrival = this.arrivals.asMap().remove(player.uniqueId());
        if (arrival == null) return;
        if (arrival.invalid) {
            player.sendMessage(MessageConstants.COMMAND_TP_OFFLINE_INVALID);
        } else {
            this.arrived(player, arrival.teleport);
        }
    }

    @Override
    public void onQuit(@NotNull BukkitSparrowPlayer player) {
        InvulnerabilityProcessor.clear(player);
    }

    // 注册一种处理器, 之后 teleport.yml 的 processor-type 可以写它的类型. 类型表只在读取 teleport.yml 时查询,
    // 其他插件应在 onLoad 中注册, 更晚注册的类型在下次重载配置后生效. 类型已被注册时抛出 IllegalArgumentException
    public void registerProcessor(@NotNull SparrowKey type, @NotNull Class<? extends TeleportProcessor> processorType) {
        this.plugin.configurationManager().teleportConfig().registerProcessor(type, processorType);
    }

    public void shutdown() {
        this.warmupManager.shutdown();
        this.plugin.playerManager().unregisterListener(this);
        this.arrivals.invalidateAll();
    }

    @NotNull
    public WarmupManager warmupManager() {
        return this.warmupManager;
    }

    // invalid 表示玩家进服时落点所在的世界已经不存在
    private record Arrival(Teleport teleport, boolean invalid) {
    }
}
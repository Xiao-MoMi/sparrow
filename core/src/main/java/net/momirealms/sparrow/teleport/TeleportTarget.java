package net.momirealms.sparrow.teleport;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.netty.channel.Channel;
import io.papermc.paper.connection.DisconnectionReason;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.kyori.adventure.text.Component;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.message.proxy.ConnectRequest;
import net.momirealms.sparrow.player.message.proxy.ConnectResult;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.proxy.paper.connection.ReadablePlayerCookieConnectionProxy;
import net.momirealms.sparrow.teleport.event.TeleportReadyEvent;
import net.momirealms.sparrow.teleport.event.TeleportRequestEvent;
import net.momirealms.sparrow.teleport.message.TeleportExecuteRequest;
import net.momirealms.sparrow.teleport.message.TeleportPrepareRequest;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessorChain;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.EventUtils;
import net.momirealms.sparrow.util.ThrowableUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class TeleportTarget implements Listener {
    private static final long ARRIVAL_SECONDS = 8; // 目标服等待玩家到达的秒数, 从收到执行请求时开始计时.

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final String serverId = ServerConfig.serverId();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, Arrival> arrivals = new ConcurrentChainedObject2ObjectHashTable<>();

    void onEnable() {
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        this.plugin.playerManager().registerJoinListener(this::onJoin);
    }

    // 进行传送准备.
    @NotNull
    CompletableFuture<TeleportPosition.Result> acceptTeleportPrepare(@NotNull TeleportPrepareRequest request) {
        TeleportService service = this.plugin.teleportService();
        synchronized (service.stateLock()) {
            if (!service.running()) {
                return CompletableFuture.completedFuture(new TeleportPosition.Result.Fail(MessageConstants.TELEPORT_DISABLED));
            }
        }
        TeleportContext context = request.context();
        UUID playerId = context.toNbt().getUUID(TeleportContext.PLAYER_UUID);
        String playerName = context.toNbt().getString(TeleportContext.PLAYER_NAME);
        // 两个传送事件都是异步事件, 只能在异步线程上触发
        Executor async = this.plugin.scheduler().async();
        return CompletableFuture.supplyAsync(() -> {
                    // 触发 TeleportRequestEvent
                    TeleportRequestEvent event = new TeleportRequestEvent(
                            request.requestId(),
                            playerId,
                            playerName,
                            request.sourceServer(),
                            this.serverId,
                            request.context(),
                            request.position()
                    );
                    Bukkit.getPluginManager().callEvent(event);
                    return event.isCancelled() ? new TeleportPosition.Result.Fail(event.reason()) : null;
                }, async)
                .thenCompose(rejection -> rejection != null ? CompletableFuture.completedFuture(rejection) : request.position().resolve(context))
                .thenCompose(resolved -> {
                    // 用目标处理器链检查解析出的坐标.
                    if (!(resolved instanceof TeleportPosition.Result.Success success)) return CompletableFuture.completedFuture(resolved);
                    List<PositionProcessor> processors = request.targetProcessors();
                    return new PositionProcessorChain(context, success.location(), processors)
                            .process()
                            .thenApply(outcome -> switch (outcome) {
                                case PositionProcessor.Result.Accept ignored -> success;
                                case PositionProcessor.Result.Relocate relocate -> new TeleportPosition.Result.Success(relocate.location());
                                case PositionProcessor.Result.Reject reject -> new TeleportPosition.Result.Fail(reject.reason());
                            });
                })
                .thenApplyAsync(checked -> {
                    // 确认目的地所在的世界已经加载, 再触发 TeleportReadyEvent.
                    if (!(checked instanceof TeleportPosition.Result.Success success)) return checked;
                    WorldLocation location = success.location();
                    if (location.resolve() == null) return new TeleportPosition.Result.Fail(MessageConstants.TELEPORT_REJECTED_INVALID);
                    TeleportReadyEvent event = new TeleportReadyEvent(
                            request.requestId(),
                            playerId,
                            playerName,
                            request.sourceServer(),
                            this.serverId,
                            request.context(),
                            location
                    );
                    return EventUtils.fireAndCheckCancel(event)
                            ? new TeleportPosition.Result.Fail(event.reason())
                            : success;
                }, async);
    }

    // 接受跨服传送请求, 登记玩家的到达, 然后发消息给代理把玩家切过来.
    @NotNull
    CompletableFuture<TeleportResult> acceptTeleportExecute(@NotNull TeleportExecuteRequest request) {
        UUID playerId = request.context().toNbt().getUUID(TeleportContext.PLAYER_UUID);
        Arrival arrival = new Arrival(playerId, request.location(), request.context(), request.postProcessors());
        TeleportService service = this.plugin.teleportService();
        synchronized (service.stateLock()) {
            if (!service.running()) return CompletableFuture.completedFuture(new TeleportResult.Fail(MessageConstants.TELEPORT_DISABLED));
            if (this.arrivals.putIfAbsent(playerId, arrival) != null) return CompletableFuture.completedFuture(new TeleportResult.Fail(MessageConstants.TELEPORT_BUSY));
        }
        arrival.start(request.sourceServer());
        return arrival.result;
    }

    // 玩家进入本服时, 如果玩家有待到达的传送时, 把出生点换成目的地.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrivalSpawn(@NotNull AsyncPlayerSpawnLocationEvent event) {
        Connection connection = (Connection) ReadablePlayerCookieConnectionProxy.INSTANCE.getConnection(event.getConnection());
        Arrival arrival = this.arrivals.get(event.getConnection().getProfile().getId());
        if (arrival == null) return;
        // 只认第一次登录. 记下它的连接, 进服时用来确认还是同一次登录
        synchronized (arrival) {
            if (arrival.result.isDone() || arrival.channel != null) return;
            arrival.channel = connection.channel;
        }
        Location location = arrival.location.resolve();
        if (location == null) {
            // 玩家已经离开源服, 目的地失效时只能断开连接
            arrival.result.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_REJECTED_INVALID));
            // Sparrow 打包时重定位了 Adventure, 它的 Component 不能交给 Paper 的 API, 断开原因先转成原版组件
            Component reason = this.plugin.translationManager().render(MessageConstants.TELEPORT_REJECTED_INVALID, null);
            ((ServerCommonPacketListenerImpl) connection.getPacketListener()).disconnect(
                    CraftChatMessage.fromJSON(AdventureHelper.componentToJson(reason)),
                    DisconnectionReason.game(PlayerKickEvent.Cause.PLUGIN)
            );
            return;
        }
        event.setSpawnLocation(location);
    }

    // 处理跨服到达
    void onJoin(BukkitSparrowPlayer player) {
        Arrival arrival = this.arrivals.get(player.uniqueId());
        if (arrival == null) return;
        // 连接对不上说明是另一次登录, 和这次传送无关
        synchronized (arrival) {
            if (arrival.channel != player.connection.channel() || arrival.arrived != null) return;
            arrival.arrived = player;
        }
        arrival.complete();
    }

    // 在异步线程上依次执行后处理器.
    void runPost(BukkitSparrowPlayer player, TeleportContext context, List<PostProcessor> processors) {
        CompletableFuture.completedFuture(null)
                .thenComposeAsync(ignored -> this.post(player, context, processors, 0), this.plugin.scheduler().async())
                .exceptionally(error -> {
                    this.plugin.logger().warn("Failed to run teleport post processors for " + player.uniqueId(), ThrowableUtils.unwrap(error));
                    return null;
                });
    }

    //  执行第 index 个后处理器, 完成后接着执行下一个. 服务已经关闭或玩家已经离开本服时, 剩下的后处理器全部跳过.
    private CompletableFuture<Void> post(BukkitSparrowPlayer player, TeleportContext context, List<PostProcessor> processors, int index) {
        if (index == processors.size()) return CompletableFuture.completedFuture(null);
        TeleportService service = this.plugin.teleportService();
        synchronized (service.stateLock()) {
            if (!service.running() || this.plugin.playerManager().getOnlinePlayer(player.platformPlayer) != player) return CompletableFuture.completedFuture(null);
        }
        return processors.get(index).process(player.platformPlayer, context)
                .thenComposeAsync(ignored -> this.post(player, context, processors, index + 1), this.plugin.scheduler().async());
    }

    void shutdown() {
        List<Arrival> arrivals;
        synchronized (this.plugin.teleportService().stateLock()) {
            arrivals = List.copyOf(this.arrivals.values());
        }
        // 到达结果的通知可以重入传送入口, 在释放生命周期锁后结算.
        for (int i = 0, size = arrivals.size(); i < size; i++) {
            arrivals.get(i).result.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_DISABLED));
        }
    }

    // 目标服上一次待到达的跨服传送. 代理确认切服成功, 并且玩家真的进了服才算到达.
    private final class Arrival {
        private final UUID playerId;                                                        // 要到达的玩家
        private final WorldLocation location;                                               // 目的地坐标, 玩家选出生点时使用
        private final TeleportContext context;                                              // 传送上下文, 交给后处理器
        private final List<PostProcessor> postProcessors;                                   // 到达后执行的后处理器
        private final CompletableFuture<TeleportResult> result = new CompletableFuture<>(); // 到达结果, 也是回复给源服的执行结果
        private @Nullable Channel channel;             // 选定出生点的连接, 进服时据此确认是同一次登录
        private @Nullable BukkitSparrowPlayer arrived; // 已经进服的玩家, 进服前为 null
        private boolean connected;                     // 代理是否已经确认切服成功

        private Arrival(UUID playerId, WorldLocation location, TeleportContext context, List<PostProcessor> postProcessors) {
            this.playerId = playerId;
            this.location = location;
            this.context = context;
            this.postProcessors = postProcessors;
        }

        // 开始等待到达. 启动到达超时, 再通知代理把玩家从源服切到本服.
        private void start(String sourceServer) {
            SchedulerTask timeout = TeleportTarget.this.plugin.scheduler().asyncLater(
                    () -> this.result.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_EXECUTION_TIMEOUT)),
                    ARRIVAL_SECONDS,
                    TimeUnit.SECONDS
            );
            // 不管怎么结束, 都停掉计时并移出登记
            this.result.whenComplete((outcome, error) -> {
                timeout.cancel();
                TeleportTarget.this.arrivals.remove(this.playerId, this);
            });
            // 代理 5 秒内没有回复按执行超时失败, 玩家已经离线按 TELEPORT_UNAVAILABLE 失败
            TeleportTarget.this.plugin.messageBrokerManager().proxyBroker()
                    .publishTwoWay(new ConnectRequest(this.playerId, sourceServer, TeleportTarget.this.serverId), "proxy")
                    .orTimeout(5, TimeUnit.SECONDS)
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            Throwable cause = ThrowableUtils.unwrap(error);
                            if (cause instanceof TimeoutException) {
                                this.result.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_EXECUTION_TIMEOUT));
                            } else {
                                this.result.completeExceptionally(cause);
                            }
                            return;
                        }
                        if (response.result() != ConnectResult.SUCCESS) {
                            Component reason = response.result() == ConnectResult.PLAYER_OFFLINE ? MessageConstants.TELEPORT_UNAVAILABLE : MessageConstants.TELEPORT_FAILED;
                            this.result.complete(new TeleportResult.Fail(reason));
                            return;
                        }
                        synchronized (this) {
                            this.connected = true;
                        }
                        this.complete();
                    });
        }

        //  代理确认和玩家进服都已经发生时结算到达. 只有真正完成结果的那次调用启动后处理器.
        private void complete() {
            BukkitSparrowPlayer player;
            synchronized (this) {
                if (!this.connected || this.arrived == null) return;
                player = this.arrived;
            }
            if (this.result.complete(new TeleportResult.Success())) {
                TeleportTarget.this.runPost(player, this.context, this.postProcessors);
            }
        }
    }
}
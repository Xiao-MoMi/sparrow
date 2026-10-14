package net.momirealms.sparrow.teleport;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.netty.channel.Channel;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.back.BackFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerConnection;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.redis.proxy.ConnectRequest;
import net.momirealms.sparrow.redis.proxy.ConnectResponse;
import net.momirealms.sparrow.redis.proxy.ConnectResult;
import net.momirealms.sparrow.teleport.event.TeleportReadyEvent;
import net.momirealms.sparrow.teleport.event.TeleportRequestEvent;
import net.momirealms.sparrow.teleport.message.TeleportCancelMessage;
import net.momirealms.sparrow.teleport.message.TeleportExecuteRequest;
import net.momirealms.sparrow.teleport.message.TeleportPrepareRequest;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessorChain;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class TeleportService implements PlayerListener {
    private static final long ACCEPT_NANOS = TimeUnit.SECONDS.toNanos(12);
    private static final long READY_NANOS = TimeUnit.SECONDS.toNanos(15);
    private static final long EXECUTION_NANOS = TimeUnit.SECONDS.toNanos(15);

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final String serverId = ServerConfig.serverId();
    private final ConcurrentChainedObject2ObjectHashTable<PlayerConnection, Submission> pendingSubmissions = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, Submission> executingSubmissions = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, TargetTask> preparations = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, TargetTask> executions = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, TargetTask> arrivals = new ConcurrentChainedObject2ObjectHashTable<>();
    private final Object stateLock = new Object();
    private @Nullable Listener arrivalListener;
    private boolean running;

    public void onEnable() {
        synchronized (this.stateLock) {
            this.running = true;
        }
        this.plugin.playerManager().registerListener(this);
        this.arrivalListener = VersionHelper.hasPaperPatch ? new PaperArrivalListener() : new SpigotArrivalListener();
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this.arrivalListener, this.plugin.javaPlugin());
    }

    @NotNull
    TeleportSubmission submit(Teleport teleport, Player player) {
        return this.submitLocal(teleport, this.plugin.playerManager().getPlayer(player));
    }

    @NotNull
    TeleportSubmission submit(Teleport teleport, SparrowPlayer player) {
        BukkitSparrowPlayer local = this.plugin.playerManager().getPlayer(player.uniqueId());
        return this.submitLocal(teleport, local == player ? local : null);
    }

    private TeleportSubmission submitLocal(Teleport teleport, @Nullable BukkitSparrowPlayer player) {
        if (player == null) return new Rejected(MessageConstants.TELEPORT_UNAVAILABLE);
        Supplier<CompletionStage<String>> server = teleport.getServer();
        TeleportPosition position = teleport.getPosition();
        Submission submission;
        Runnable replaced = null;
        synchronized (this.stateLock) {
            if (!this.running) return new Rejected(MessageConstants.TELEPORT_DISABLED);
            if (this.plugin.playerManager().getPlayer(player.platformPlayer()) != player) return new Rejected(MessageConstants.TELEPORT_UNAVAILABLE);
            Submission previous = this.pendingSubmissions.get(player.connection());
            submission = new Submission(
                    player,
                    teleport.createContext(player),
                    position,
                    List.copyOf(teleport.targetProcessors()),
                    List.copyOf(teleport.postProcessors())
            );
            if (previous != null) {
                replaced = previous.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_REPLACED), null);
            }
            this.pendingSubmissions.put(player.connection(), submission);
        }
        // Future 回调可能重新提交传送, 所有通知都在释放状态锁后执行
        if (replaced != null) {
            replaced.run();
        }
        submission.stage(State.PREPARING, () -> server.get().toCompletableFuture(), submission::prepare, this.plugin.scheduler().async());
        return submission;
    }

    @NotNull
    public CompletableFuture<TeleportPosition.Result> prepare(@NotNull TeleportPrepareRequest request) {
        TargetTask task = new TargetTask(
                request.requestId(),
                request.sourceServer(),
                request.context(),
                request.position(),
                request.targetProcessors(),
                request.postProcessors()
        );
        synchronized (this.stateLock) {
            if (!this.running) return CompletableFuture.completedFuture(new TeleportPosition.Result.Fail(MessageConstants.TELEPORT_DISABLED));
            this.preparations.put(task.requestId, task);
        }
        return task.prepare()
                .thenApply(result -> {
                    if (result instanceof TeleportPosition.Result.Fail) {
                        synchronized (this.stateLock) {
                            this.preparations.remove(task.requestId, task);
                        }
                        return result;
                    }
                    WorldLocation location = ((TeleportPosition.Result.Success) result).location();
                    synchronized (this.stateLock) {
                        if (this.preparations.get(task.requestId) != task) {
                            return new TeleportPosition.Result.Fail(MessageConstants.TELEPORT_CANCELLED);
                        }
                        task.location = location;
                        task.readyDeadline = System.nanoTime() + READY_NANOS;
                        // 回复之前保存 READY 并安排过期, commitForget 才能立即接受
                        task.expiry = this.plugin.scheduler().asyncLater(() -> this.expirePreparation(task), READY_NANOS, TimeUnit.NANOSECONDS);
                    }
                    return result;
                })
                .whenComplete((result, error) -> {
                    task.position = null;
                    task.processors = List.of();
                    if (error != null) {
                        this.cancel(task.requestId);
                    }
                });
    }

    @NotNull
    public CompletableFuture<TeleportResult> execute(@NotNull UUID requestId, @NotNull String sourceServer) {
        TargetTask task;
        Component rejection = null;
        synchronized (this.stateLock) {
            if (!this.running) return CompletableFuture.completedFuture(new TeleportResult.Fail(MessageConstants.TELEPORT_DISABLED));
            if (this.executions.get(requestId) != null) {
                return CompletableFuture.completedFuture(new TeleportResult.Fail(MessageConstants.TELEPORT_BUSY));
            }
            task = this.preparations.get(requestId);
            if (task == null) return CompletableFuture.completedFuture(new TeleportResult.Fail(MessageConstants.TELEPORT_EXPIRED));
            if (!task.sourceServer.equals(sourceServer)) {
                return CompletableFuture.completedFuture(new TeleportResult.Fail(MessageConstants.TELEPORT_FAILED));
            }
            this.preparations.remove(task.requestId, task);
            if (task.location == null || System.nanoTime() - task.readyDeadline >= 0L) {
                rejection = MessageConstants.TELEPORT_EXPIRED;
            } else if (this.executingSubmissions.get(task.playerId) != null || this.arrivals.putIfAbsent(task.playerId, task) != null) {
                rejection = MessageConstants.TELEPORT_BUSY;
            } else {
                this.executions.put(task.requestId, task);
            }
        }
        if (rejection != null) {
            task.discard(rejection);
            return CompletableFuture.completedFuture(new TeleportResult.Fail(rejection));
        }
        CompletableFuture<TeleportResult> completion = task.execution.whenComplete((result, error) -> {
            // 先解除索引, 再回复 A, 下一次提交才不会碰到已结束的到达任务
            synchronized (this.stateLock) {
                this.executions.remove(task.requestId, task);
                this.arrivals.remove(task.playerId, task);
            }
        });
        task.cancelExpiry();
        CompletableFuture.runAsync(task::connect, this.plugin.scheduler().async()).exceptionally(error -> {
            task.execution.completeExceptionally(error);
            return null;
        });
        return completion;
    }

    @Nullable
    Location selectSpawn(UUID playerId, Channel channel) {
        TargetTask task;
        Location location;
        synchronized (this.stateLock) {
            task = this.arrivals.get(playerId);
            if (task == null || task.execution.isDone() || task.arrivalChannel != null) return null;
            location = task.location.resolve();
            if (location != null) {
                task.arrivalChannel = channel;
            }
        }
        if (location == null) {
            task.execution.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_REJECTED_INVALID));
        }
        return location;
    }

    public void cancel(@NotNull UUID requestId) {
        TargetTask task;
        synchronized (this.stateLock) {
            task = this.preparations.remove(requestId);
            if (task == null) {
                task = this.executions.get(requestId);
            }
        }
        if (task != null) {
            task.discard(MessageConstants.TELEPORT_CANCELLED);
        }
    }

    private void expirePreparation(TargetTask task) {
        synchronized (this.stateLock) {
            if (this.preparations.remove(task.requestId, task) == null) return;
        }
        task.discard(MessageConstants.TELEPORT_EXPIRED);
    }

    void forget(TeleportSubmission submission, UUID playerId) {
        BukkitSparrowPlayer player = this.plugin.playerManager().getPlayer(playerId);
        submission.result()
                .thenCompose(result -> switch (result) {
                    case TeleportPreparationResult.Success success -> success.accept();
                    case TeleportPreparationResult.Fail fail -> CompletableFuture.completedFuture(new TeleportResult.Fail(fail.reason()));
                })
                .whenComplete((result, error) -> {
                    Component reason;
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                        this.plugin.logger().warn("Teleport " + submission.requestId() + " failed for " + playerId, cause);
                        reason = MessageConstants.TELEPORT_FAILED;
                    } else if (result instanceof TeleportResult.Fail fail) {
                        reason = fail.reason();
                        if (MessageConstants.TELEPORT_REPLACED.equals(reason)) return;
                    } else {
                        return;
                    }
                    if (player != null && this.plugin.playerManager().getPlayer(player.platformPlayer()) == player) {
                        player.sendMessage(player.render(reason));
                    }
                });
    }

    @Override
    public void onJoin(@NotNull BukkitSparrowPlayer player) {
        TargetTask task;
        synchronized (this.stateLock) {
            task = this.arrivals.get(player.uniqueId());
            if (task == null || task.arrivalChannel != player.connection().channel() || task.arrived != null) return;
            task.arrived = player;
        }
        task.completeArrival();
    }

    @Override
    public void onQuit(@NotNull BukkitSparrowPlayer player) {
        Runnable pendingCompletion = null;
        Runnable executionCompletion = null;
        TargetTask incoming;
        synchronized (this.stateLock) {
            Submission submission = this.pendingSubmissions.get(player.connection());
            if (submission != null) {
                pendingCompletion = submission.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_UNAVAILABLE), null);
            }
            Submission executing = this.executingSubmissions.get(player.uniqueId());
            if (executing != null && executing.player.connection() == player.connection() && !executing.isRemote()) {
                executionCompletion = executing.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_UNAVAILABLE), null);
            }
            incoming = this.arrivals.get(player.uniqueId());
            if (incoming != null && incoming.arrivalChannel != player.connection().channel()) {
                incoming = null;
            }
        }
        if (pendingCompletion != null) pendingCompletion.run();
        if (executionCompletion != null) executionCompletion.run();
        if (incoming != null) incoming.execution.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_UNAVAILABLE));
        Invulnerability.clear(player);
    }

    public void shutdown() {
        List<Runnable> completions = new ArrayList<>();
        List<TargetTask> targets;
        synchronized (this.stateLock) {
            this.running = false;
            for (Submission submission : List.copyOf(this.pendingSubmissions.values())) {
                completions.add(submission.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_DISABLED), null));
            }
            for (Submission submission : List.copyOf(this.executingSubmissions.values())) {
                completions.add(submission.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_DISABLED), null));
            }
            targets = new ArrayList<>(this.preparations.values());
            targets.addAll(this.executions.values());
            this.preparations.clear();
            this.executions.clear();
            this.arrivals.clear();
        }
        for (Runnable completion : completions) {
            completion.run();
        }
        for (TargetTask task : targets) {
            task.discard(MessageConstants.TELEPORT_DISABLED);
        }
        if (this.arrivalListener != null) {
            HandlerList.unregisterAll(this.arrivalListener);
            this.arrivalListener = null;
        }
        this.plugin.playerManager().unregisterListener(this);
        for (BukkitSparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            Invulnerability.clear(player);
        }
    }

    private enum State {
        PREPARING, READY, EXECUTING, FINISHED
    }

    private final class Submission implements TeleportSubmission {
        private final UUID requestId = UUID.randomUUID();
        private final BukkitSparrowPlayer player;
        private @Nullable TeleportPrepareRequest request;
        private @Nullable String server;
        private final CompletableFuture<TeleportPreparationResult> result = new CompletableFuture<>();
        private final CompletableFuture<TeleportResult> execution = new CompletableFuture<>();
        private State state = State.PREPARING;
        private @Nullable CompletableFuture<?> pending;
        private @Nullable SchedulerTask timeout;
        private long deadline;

        private Submission(
                BukkitSparrowPlayer player,
                TeleportContext context,
                TeleportPosition position,
                List<PositionProcessor> processors,
                List<PostProcessor> postProcessors
        ) {
            this.player = player;
            this.request = new TeleportPrepareRequest(this.requestId, context, position, processors, postProcessors);
            this.request.setSourceServer(TeleportService.this.serverId);
        }

        @NotNull
        @Override
        public UUID requestId() {
            return this.requestId;
        }

        @NotNull
        @Override
        public CompletableFuture<TeleportPreparationResult> result() {
            return this.result;
        }

        private void prepare(String server) {
            TeleportPrepareRequest request;
            synchronized (TeleportService.this.stateLock) {
                if (this.state != State.PREPARING) return;
                this.server = server;
                request = this.request;
                this.request = null;
            }
            request.setTargetServer(server);
            this.stage(State.PREPARING, () -> this.requestPreparation(request, server), this::ready, TeleportService.this.plugin.scheduler().async());
        }

        private CompletableFuture<TeleportPreparationResult> requestPreparation(TeleportPrepareRequest request, String server) {
            CompletableFuture<TeleportPreparationResult> received = new CompletableFuture<>();
            CompletableFuture<TeleportPreparationResult> response;
            if (server.equals(TeleportService.this.serverId)) {
                response = TeleportService.this.prepare(request).thenApply(result -> switch (result) {
                    case TeleportPosition.Result.Success success -> new Prepared(this, server, success.location(), request.context());
                    case TeleportPosition.Result.Fail fail -> new TeleportPreparationResult.Fail(fail.reason());
                });
            } else {
                response = TeleportService.this.plugin.messageBrokerManager().broker()
                        .publishTwoWay(request, server)
                        .thenApply(result -> {
                            if (!result.requestId().equals(this.requestId)) throw new IllegalStateException("Teleport preparation response has a different request ID");
                            return result.successful()
                                    ? new Prepared(this, server, result.location(), result.context())
                                    : new TeleportPreparationResult.Fail(result.reason());
                        });
            }
            // 保留原始 RPC 的回复回调. 取消可能早于请求真正发出, 迟到成功仍须释放 B 的 READY
            response.whenComplete((result, error) -> {
                if (error != null) {
                    received.completeExceptionally(error);
                    return;
                }
                boolean discarded;
                boolean successful = result instanceof TeleportPreparationResult.Success;
                synchronized (TeleportService.this.stateLock) {
                    discarded = this.state != State.PREPARING;
                    if (!discarded && successful) {
                        this.deadline = System.nanoTime() + ACCEPT_NANOS;
                    }
                }
                if (discarded && successful) {
                    this.releaseTarget();
                }
                received.complete(result);
            });
            return received;
        }

        private void ready(TeleportPreparationResult response) {
            if (response instanceof TeleportPreparationResult.Fail fail) {
                this.finish(new TeleportResult.Fail(fail.reason()), null);
                return;
            }
            Runnable expired = null;
            synchronized (TeleportService.this.stateLock) {
                if (this.state != State.PREPARING) return;
                if (System.nanoTime() - this.deadline >= 0L) {
                    expired = this.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_EXPIRED), null);
                } else {
                    this.state = State.READY;
                    this.timeout = TeleportService.this.plugin.scheduler().asyncLater(
                            this::expireReady,
                            this.deadline - System.nanoTime(),
                            TimeUnit.NANOSECONDS
                    );
                }
            }
            if (expired != null) {
                expired.run();
                return;
            }
            this.result.complete(response);
        }

        private void expireReady() {
            Runnable completion;
            synchronized (TeleportService.this.stateLock) {
                if (this.state != State.READY) return;
                completion = this.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_EXPIRED), null);
            }
            completion.run();
        }

        private void releaseTarget() {
            String server = this.server;
            if (server == null) return;
            if (server.equals(TeleportService.this.serverId)) {
                TeleportService.this.cancel(this.requestId);
            } else {
                TeleportService.this.plugin.messageBrokerManager().broker().publishOneWay(new TeleportCancelMessage(this.requestId), server);
            }
        }

        @Override
        public boolean cancel() {
            Runnable completion;
            synchronized (TeleportService.this.stateLock) {
                if (this.state != State.PREPARING && this.state != State.READY) return false;
                completion = this.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_CANCELLED), null);
            }
            completion.run();
            return true;
        }

        private CompletableFuture<TeleportResult> accept() {
            Runnable rejected = null;
            TargetTask target = null;
            synchronized (TeleportService.this.stateLock) {
                if (this.state != State.READY) return this.execution;
                if (System.nanoTime() - this.deadline >= 0L) {
                    rejected = this.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_EXPIRED), null);
                } else if (TeleportService.this.plugin.playerManager().getPlayer(this.player.platformPlayer()) != this.player) {
                    rejected = this.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_UNAVAILABLE), null);
                } else if (TeleportService.this.executingSubmissions.get(this.player.uniqueId()) != null
                        || TeleportService.this.arrivals.get(this.player.uniqueId()) != null) {
                    rejected = this.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_BUSY), null);
                } else if (!this.isRemote()) {
                    target = TeleportService.this.preparations.remove(this.requestId);
                    if (target == null || System.nanoTime() - target.readyDeadline >= 0L) {
                        rejected = this.finishLocked(new TeleportResult.Fail(MessageConstants.TELEPORT_EXPIRED), null);
                    }
                }
                if (rejected == null) {
                    this.timeout.cancel();
                    this.timeout = null;
                    this.state = State.EXECUTING;
                    this.deadline = this.isRemote() ? 0L : System.nanoTime() + EXECUTION_NANOS;
                    // 接受与执行占用在同一个锁内交接, 后续提交可以独立准备
                    TeleportService.this.pendingSubmissions.remove(this.player.connection(), this);
                    TeleportService.this.executingSubmissions.put(this.player.uniqueId(), this);
                }
            }
            if (target != null) {
                target.cancelExpiry();
            }
            if (rejected != null) {
                rejected.run();
                return this.execution;
            }
            if (this.isRemote()) {
                this.stage(State.EXECUTING, this::executeRemote, result -> this.finish(result, null), TeleportService.this.plugin.scheduler().async());
                return this.execution;
            }
            SchedulerTask timer;
            try {
                timer = TeleportService.this.plugin.scheduler().asyncLater(
                        () -> this.finish(new TeleportResult.Fail(MessageConstants.TELEPORT_EXECUTION_TIMEOUT), null),
                        Math.max(0L, this.deadline - System.nanoTime()),
                        TimeUnit.NANOSECONDS
                );
            } catch (RejectedExecutionException error) {
                this.finish(null, error);
                return this.execution;
            }
            synchronized (TeleportService.this.stateLock) {
                if (this.state != State.EXECUTING) {
                    timer.cancel();
                    return this.execution;
                }
                this.timeout = timer;
            }
            TargetTask accepted = target;
            this.stage(
                    State.EXECUTING,
                    () -> accepted.executeLocal(this.player, accepted.location, this.deadline),
                    result -> this.finish(result, null),
                    TeleportService.this.plugin.scheduler().async()
            );
            return this.execution;
        }

        private boolean isRemote() {
            return this.server != null && !this.server.equals(TeleportService.this.serverId);
        }

        private CompletableFuture<TeleportResult> executeRemote() {
            return TeleportService.this.plugin.messageBrokerManager().broker()
                    .publishTwoWay(new TeleportExecuteRequest(this.requestId), this.server)
                    .handle((response, error) -> {
                        if (error != null) {
                            Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                            if (cause instanceof TimeoutException) return new TeleportResult.Fail(MessageConstants.TELEPORT_EXECUTION_TIMEOUT);
                            throw new CompletionException(cause);
                        }
                        if (!response.requestId().equals(this.requestId)) throw new IllegalStateException("Teleport execution response has a different request ID");
                        return response.result();
                    });
        }

        // 每次仅持有一个活动阶段. 取消会传到提供器或处理器返回的原始 Future
        private <T> void stage(State expected, Supplier<CompletableFuture<T>> action, Consumer<T> continuation, Executor executor) {
            try {
                CompletableFuture.runAsync(() -> {
                    if (!this.active(expected)) return;
                    CompletableFuture<T> future = action.get();
                    synchronized (TeleportService.this.stateLock) {
                        if (this.state == expected) {
                            this.pending = future;
                        }
                    }
                    if (!this.active(expected)) {
                        future.cancel(false);
                        return;
                    }
                    future.whenCompleteAsync((value, error) -> {
                        if (!this.active(expected)) return;
                        synchronized (TeleportService.this.stateLock) {
                            if (this.pending == future) {
                                this.pending = null;
                            }
                        }
                        if (error != null) {
                            this.finish(null, error);
                        } else {
                            continuation.accept(value);
                        }
                    }, TeleportService.this.plugin.scheduler().async()).exceptionally(error -> {
                        this.finish(null, error);
                        return null;
                    });
                }, executor).exceptionally(error -> {
                    this.finish(null, error);
                    return null;
                });
            } catch (RejectedExecutionException error) {
                this.finish(null, error);
            }
        }

        private boolean active(State expected) {
            Component failure = null;
            synchronized (TeleportService.this.stateLock) {
                if (this.state != expected) return false;
                if (expected == State.EXECUTING && this.isRemote()) return true;
                if (TeleportService.this.plugin.playerManager().getPlayer(this.player.platformPlayer()) != this.player) {
                    failure = MessageConstants.TELEPORT_UNAVAILABLE;
                } else if (expected == State.EXECUTING && System.nanoTime() - this.deadline >= 0L) {
                    failure = MessageConstants.TELEPORT_EXECUTION_TIMEOUT;
                }
            }
            if (failure == null) return true;
            this.finish(new TeleportResult.Fail(failure), null);
            return false;
        }

        private void finish(@Nullable TeleportResult result, @Nullable Throwable error) {
            Runnable completion;
            synchronized (TeleportService.this.stateLock) {
                completion = this.finishLocked(result, error);
            }
            completion.run();
        }

        private Runnable finishLocked(@Nullable TeleportResult outcome, @Nullable Throwable error) {
            if (this.state == State.FINISHED) return () -> {};
            TeleportResult finalOutcome = this.state == State.EXECUTING && !this.isRemote() && System.nanoTime() - this.deadline >= 0L
                    ? new TeleportResult.Fail(MessageConstants.TELEPORT_EXECUTION_TIMEOUT)
                    : outcome;
            this.state = State.FINISHED;
            TeleportService.this.pendingSubmissions.remove(this.player.connection(), this);
            TeleportService.this.executingSubmissions.remove(this.player.uniqueId(), this);
            CompletableFuture<?> current = this.pending;
            SchedulerTask timer = this.timeout;
            this.pending = null;
            this.timeout = null;
            this.request = null;
            return () -> {
                if (timer != null) timer.cancel();
                if (current != null) current.cancel(false);
                if (error != null) {
                    this.result.completeExceptionally(error);
                    this.execution.completeExceptionally(error);
                } else {
                    if (finalOutcome instanceof TeleportResult.Fail fail) {
                        this.result.complete(new TeleportPreparationResult.Fail(fail.reason()));
                    }
                    this.execution.complete(finalOutcome);
                }
                if (!(finalOutcome instanceof TeleportResult.Success)) {
                    this.releaseTarget();
                }
            };
        }
    }

    private final class TargetTask {
        private final UUID requestId;
        private final String sourceServer;
        private final UUID playerId;
        private final String playerName;
        private final TeleportContext context;
        private @Nullable TeleportPosition position;
        private List<PositionProcessor> processors;
        private final List<PostProcessor> postProcessors;
        private final CompletableFuture<TeleportPosition.Result> preparation = new CompletableFuture<>();
        private final CompletableFuture<TeleportResult> execution = new CompletableFuture<>();
        private volatile @Nullable CompletableFuture<?> preparing;
        private volatile @Nullable CompletableFuture<?> executing;
        private @Nullable WorldLocation location;
        private @Nullable SchedulerTask expiry;
        private long readyDeadline;
        private long localDeadline;
        private @Nullable Channel arrivalChannel;
        private @Nullable BukkitSparrowPlayer arrived;
        private boolean connected;

        private TargetTask(
                UUID requestId,
                String sourceServer,
                TeleportContext context,
                TeleportPosition position,
                List<PositionProcessor> processors,
                List<PostProcessor> postProcessors
        ) {
            this.requestId = requestId;
            this.sourceServer = sourceServer;
            this.playerId = context.toNbt().getUUID(TeleportContext.PLAYER_UUID);
            this.playerName = context.toNbt().getString(TeleportContext.PLAYER_NAME);
            this.context = context;
            this.position = position;
            this.processors = processors;
            this.postProcessors = postProcessors;
            this.preparation.whenComplete((result, error) -> {
                CompletableFuture<?> pending = this.preparing;
                this.preparing = null;
                if (pending != null && (error != null || result instanceof TeleportPosition.Result.Fail)) {
                    pending.cancel(false);
                }
            });
            this.execution.whenComplete((result, error) -> {
                CompletableFuture<?> pending = this.executing;
                this.executing = null;
                if (pending != null && (error != null || result instanceof TeleportResult.Fail)) {
                    pending.cancel(false);
                }
            });
        }

        private CompletableFuture<TeleportPosition.Result> prepare() {
            Executor worker = TeleportService.this.plugin.scheduler().async();
            Executor checking = VersionHelper.hasPaperPatch ? worker : TeleportService.this.plugin.scheduler().platform();
            CompletableFuture.completedFuture(null)
                    .thenComposeAsync(ignored -> this.resolve(), worker)
                    .thenComposeAsync(this::checkPosition, checking)
                    .thenApplyAsync(this::ready, worker)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            this.preparation.completeExceptionally(error);
                        } else {
                            this.preparation.complete(result);
                        }
                    });
            return this.preparation;
        }

        private CompletableFuture<TeleportPosition.Result> resolve() {
            TeleportPosition position = this.position;
            if (this.preparation.isDone()) return this.preparation;
            TeleportRequestEvent event = new TeleportRequestEvent(
                    this.requestId,
                    this.playerId,
                    this.playerName,
                    this.sourceServer,
                    TeleportService.this.serverId,
                    this.context,
                    position
            );
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return CompletableFuture.completedFuture(new TeleportPosition.Result.Fail(event.reason()));
            if (this.preparation.isDone()) return this.preparation;
            return this.trackPreparation(position.resolve(this.context));
        }

        private CompletableFuture<TeleportPosition.Result> checkPosition(TeleportPosition.Result result) {
            if (this.preparation.isDone()) return this.preparation;
            if (result instanceof TeleportPosition.Result.Fail) return CompletableFuture.completedFuture(result);
            WorldLocation location = ((TeleportPosition.Result.Success) result).location();
            CompletableFuture<PositionProcessor.Result> checked = new PositionProcessorChain(this.context, location, this.processors).process();
            return this.trackPreparation(checked).thenApply(outcome -> switch (outcome) {
                case PositionProcessor.Result.Accept ignored -> result;
                case PositionProcessor.Result.Relocate relocate -> new TeleportPosition.Result.Success(relocate.location());
                case PositionProcessor.Result.Reject reject -> new TeleportPosition.Result.Fail(reject.reason());
            });
        }

        private TeleportPosition.Result ready(TeleportPosition.Result result) {
            if (this.preparation.isDone() || result instanceof TeleportPosition.Result.Fail) return result;
            WorldLocation location = ((TeleportPosition.Result.Success) result).location();
            if (location.resolve() == null) return new TeleportPosition.Result.Fail(MessageConstants.TELEPORT_REJECTED_INVALID);
            TeleportReadyEvent event = new TeleportReadyEvent(
                    this.requestId,
                    this.playerId,
                    this.playerName,
                    this.sourceServer,
                    TeleportService.this.serverId,
                    this.context,
                    location
            );
            Bukkit.getPluginManager().callEvent(event);
            return event.isCancelled() ? new TeleportPosition.Result.Fail(event.reason()) : result;
        }

        private <T> CompletableFuture<T> trackPreparation(CompletableFuture<T> future) {
            this.preparing = future;
            if (this.preparation.isDone()) {
                future.cancel(false);
                this.preparing = null;
            }
            return future;
        }

        private void cancelExpiry() {
            SchedulerTask expiry = this.expiry;
            this.expiry = null;
            if (expiry != null) {
                expiry.cancel();
            }
        }

        private void discard(Component reason) {
            this.cancelExpiry();
            this.preparation.complete(new TeleportPosition.Result.Fail(reason));
            this.execution.complete(new TeleportResult.Fail(reason));
            this.position = null;
            this.processors = List.of();
        }

        private void connect() {
            if (this.execution.isDone()) return;
            if (this.location.resolve() == null) {
                this.execution.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_REJECTED_INVALID));
                return;
            }
            CompletableFuture<ConnectResponse> connecting = TeleportService.this.plugin.messageBrokerManager().proxyBroker()
                    .publishTwoWay(new ConnectRequest(this.playerId, this.sourceServer, TeleportService.this.serverId), "proxy")
                    .orTimeout(5, TimeUnit.SECONDS);
            this.trackExecution(connecting).whenComplete((response, error) -> {
                if (this.execution.isDone()) return;
                if (error != null) {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        this.execution.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_EXECUTION_TIMEOUT));
                    } else {
                        this.execution.completeExceptionally(cause);
                    }
                    return;
                }
                if (response.result() != ConnectResult.SUCCESS) {
                    Component reason = response.result() == ConnectResult.PLAYER_OFFLINE ? MessageConstants.TELEPORT_UNAVAILABLE : MessageConstants.TELEPORT_FAILED;
                    this.execution.complete(new TeleportResult.Fail(reason));
                    return;
                }
                synchronized (TeleportService.this.stateLock) {
                    this.connected = true;
                }
                this.completeArrival();
            });
        }

        private void completeArrival() {
            BukkitSparrowPlayer player;
            synchronized (TeleportService.this.stateLock) {
                if (this.execution.isDone() || !this.connected || this.arrived == null) return;
                player = this.arrived;
            }
            // 代理成功与实际进服都满足时结算传送, 赢得结算的回调启动 Post
            if (this.execution.complete(new TeleportResult.Success())) {
                this.runPost(player);
            }
        }

        private void runPost(BukkitSparrowPlayer player) {
            CompletableFuture.completedFuture(null)
                    .thenComposeAsync(ignored -> this.post(player, 0), TeleportService.this.plugin.scheduler().async())
                    .exceptionally(error -> {
                        Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                        TeleportService.this.plugin.logger().warn("Failed to run teleport post processors for " + this.playerId + " (request " + this.requestId + ")", cause);
                        return null;
                    });
        }

        private CompletableFuture<TeleportResult> executeLocal(BukkitSparrowPlayer player, WorldLocation location, long deadline) {
            this.localDeadline = deadline;
            Executor worker = TeleportService.this.plugin.scheduler().async();
            Executor moving = VersionHelper.hasPaperPatch ? worker : TeleportService.this.plugin.scheduler().platform();
            CompletableFuture.completedFuture(null)
                    .thenComposeAsync(ignored -> this.move(player, location), moving)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            this.execution.completeExceptionally(error);
                        } else if (this.execution.complete(result) && result instanceof TeleportResult.Success) {
                            this.runPost(player);
                        }
                    });
            return this.execution;
        }

        private CompletableFuture<TeleportResult> move(BukkitSparrowPlayer player, WorldLocation location) {
            if (!this.activeExecution(player)) return this.execution;
            Location target = location.resolve();
            if (target == null) return CompletableFuture.completedFuture(new TeleportResult.Fail(MessageConstants.TELEPORT_REJECTED_INVALID));
            Player platformPlayer = player.platformPlayer();
            Location from = platformPlayer.getLocation();
            CompletableFuture<Boolean> movement = VersionHelper.hasPaperPatch
                    ? platformPlayer.teleportAsync(target, TeleportCause.PLUGIN)
                    : CompletableFuture.completedFuture(platformPlayer.teleport(target, TeleportCause.PLUGIN));
            return this.trackExecution(movement).thenApply(success -> {
                // Folia 不派发普通传送事件, 成功后补记本服返回点
                if (success && VersionHelper.hasFoliaPatch && this.activeExecution(player)) {
                    BackFeature back = TeleportService.this.plugin.featureManager().feature(BackFeature.ID, BackFeature.class);
                    back.recordTeleport(platformPlayer, from, target, TeleportCause.PLUGIN);
                }
                return success ? new TeleportResult.Success() : new TeleportResult.Fail(MessageConstants.TELEPORT_FAILED);
            });
        }

        private CompletableFuture<Void> post(BukkitSparrowPlayer player, int index) {
            if (index == this.postProcessors.size()) return CompletableFuture.completedFuture(null);
            synchronized (TeleportService.this.stateLock) {
                if (!TeleportService.this.running || TeleportService.this.plugin.playerManager().getPlayer(player.platformPlayer()) != player) {
                    return CompletableFuture.completedFuture(null);
                }
            }
            CompletableFuture<Void> processing = this.postProcessors.get(index).process(player.platformPlayer(), this.context);
            return processing.thenComposeAsync(ignored -> this.post(player, index + 1), TeleportService.this.plugin.scheduler().async());
        }

        private <T> CompletableFuture<T> trackExecution(CompletableFuture<T> future) {
            this.executing = future;
            if (this.execution.isDone()) {
                future.cancel(false);
                this.executing = null;
            }
            return future;
        }

        private boolean activeExecution(BukkitSparrowPlayer player) {
            if (this.execution.isDone()) return false;
            if (this.localDeadline != 0L && System.nanoTime() - this.localDeadline >= 0L) {
                this.execution.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_EXECUTION_TIMEOUT));
                return false;
            }
            if (TeleportService.this.plugin.playerManager().getPlayer(player.platformPlayer()) != player) {
                this.execution.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_UNAVAILABLE));
                return false;
            }
            return true;
        }
    }

    private record Prepared(
            Submission owner,
            @NotNull String server,
            @NotNull WorldLocation location,
            @NotNull TeleportContext context
    ) implements TeleportPreparationResult.Success {

        @NotNull
        @Override
        public CompletableFuture<TeleportResult> accept() {
            return this.owner.accept();
        }
    }

    private record Rejected(@NotNull UUID requestId, @NotNull CompletableFuture<TeleportPreparationResult> result) implements TeleportSubmission {

        private Rejected(Component reason) {
            this(UUID.randomUUID(), CompletableFuture.completedFuture(new TeleportPreparationResult.Fail(reason)));
        }

        @Override
        public boolean cancel() {
            return false;
        }
    }
}

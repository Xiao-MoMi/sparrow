package net.momirealms.sparrow.teleport;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.teleport.message.TeleportExecuteRequest;
import net.momirealms.sparrow.teleport.message.TeleportPrepareRequest;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.util.ThrowableUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

final class TeleportSource {
    private static final long LOCAL_EXECUTION_SECONDS = 15;

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final String serverId = ServerConfig.serverId();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, Submission> submissions = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, Submission> executions = new ConcurrentChainedObject2ObjectHashTable<>();

    // 提交一次传送
    TeleportSubmission submit(Teleport teleport, @Nullable BukkitSparrowPlayer player) {
        // 必填参数先取出来, 缺了就直接抛出
        Supplier<CompletionStage<String>> server = teleport.getServer();
        TeleportPosition position = teleport.getPosition();
        // 条件都转成 CompletableFuture, 后面既要监听它们完成, 也要在传送提前结束时取消它们
        List<CompletionStage<?>> stages = teleport.conditions();
        int size = stages.size();
        List<CompletableFuture<?>> conditions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            conditions.add(stages.get(i).toCompletableFuture());
        }
        if (player == null) return reject(conditions, MessageConstants.TELEPORT_UNAVAILABLE);
        Submission submission = new Submission(
                player,
                teleport.createContext(player),
                position,
                List.copyOf(teleport.targetProcessors()),
                List.copyOf(teleport.postProcessors()),
                conditions
        );
        // 运行状态检查和登记在同一把锁里完成, 关服时不会漏掉刚登记的传送
        TeleportService service = this.plugin.teleportService();
        boolean running;
        synchronized (service.stateLock()) {
            running = service.running();
            if (running) {
                this.submissions.put(submission.requestId, submission);
            }
        }
        if (!running) return reject(conditions, MessageConstants.TELEPORT_DISABLED);
        submission.start(server);
        return submission;
    }

    // 提交一次传送, 且不理会传送结果.
    void forget(@NotNull TeleportSubmission submission, @NotNull UUID playerId) {
        submission.result().whenComplete((result, error) -> {
            if (error != null) {
                this.plugin.logger().warn("Teleport " + submission.requestId() + " failed for " + playerId, ThrowableUtils.unwrap(error));
            }
        });
    }

    void shutdown() {
        List<Submission> submissions;
        synchronized (this.plugin.teleportService().stateLock()) {
            submissions = List.copyOf(this.submissions.values());
        }
        // Future 完成通知可以重入传送入口, 在释放生命周期锁后结束提交.
        for (int i = 0, size = submissions.size(); i < size; i++) {
            submissions.get(i).finish(new TeleportResult.Fail(MessageConstants.TELEPORT_DISABLED), null);
        }
    }

    /**
     * 创建一个已经失败的句柄, 同时取消调用方给的执行条件.
     *
     * @param conditions 调用方给的执行条件
     * @param reason 失败原因
     * @return 准备结果和最终结果都已经是 Fail 的句柄
     */
    private static TeleportSubmission reject(List<CompletableFuture<?>> conditions, Component reason) {
        cancelAll(conditions);
        return new Rejected(
                UUID.randomUUID(),
                CompletableFuture.completedFuture(new TeleportPreparationResult.Fail(reason)),
                CompletableFuture.completedFuture(new TeleportResult.Fail(reason))
        );
    }

    /**
     * 取消所有条件, 已经完成的条件不受影响.
     *
     * @param conditions 要取消的条件
     */
    private static void cancelAll(List<CompletableFuture<?>> conditions) {
        for (int i = 0, size = conditions.size(); i < size; i++) {
            conditions.get(i).cancel(false);
        }
    }

    /**
     * 源服上一次传送所处的阶段. 只按 PREPARING -> WAITING -> EXECUTING -> FINISHED 的顺序往前走,
     * 任何阶段都可能直接跳到 FINISHED.
     */
    private enum State {
        PREPARING, // 等目标服算出目的地
        WAITING,   // 目的地已经算好, 等执行条件
        EXECUTING, // 正在移动玩家
        FINISHED   // 已经结束, 结果已经给出
    }

    // 源服上的一次传送, 也是交给调用方的句柄.
    private final class Submission implements TeleportSubmission {
        private final UUID requestId = UUID.randomUUID();               // 这次传送的 ID, 准备和执行报文都带着它
        private final BukkitSparrowPlayer player;                       // 被传送的玩家, 也就是提交时的本服会话
        private final TeleportPosition position;                        // 目的地, 发给目标服解析
        private final List<PositionProcessor> targetProcessors;         // 发给目标服的目标处理器
        private final List<PostProcessor> postProcessors;               // 到达后执行的后处理器
        private final List<CompletableFuture<?>> conditions;            // 调用方给的执行条件
        private final CompletableFuture<TeleportPreparationResult> prepared = new CompletableFuture<>(); // 准备结果, 交给调用方观察
        private final CompletableFuture<TeleportResult> result = new CompletableFuture<>();              // 最终结果, 交给调用方观察
        private TeleportContext context;            // 传送上下文. 准备成功后换成目标服带回的版本, 执行时再带过去
        private State state = State.PREPARING;      // 当前阶段, 只在持有自身锁时读写
        private int waiting;                        // 还没完成的条件数, 归零才能执行
        private @Nullable String server;            // 目标服务器 ID, 拿到之后才开始准备
        private @Nullable WorldLocation location;   // 准备好的目的地坐标

        private Submission(
                BukkitSparrowPlayer player,
                TeleportContext context,
                TeleportPosition position,
                List<PositionProcessor> targetProcessors,
                List<PostProcessor> postProcessors,
                List<CompletableFuture<?>> conditions
        ) {
            this.player = player;
            this.context = context;
            this.position = position;
            this.targetProcessors = targetProcessors;
            this.postProcessors = postProcessors;
            this.conditions = conditions;
            this.waiting = conditions.size();
        }

        @NotNull
        @Override
        public UUID requestId() {
            return this.requestId;
        }

        @NotNull
        @Override
        public CompletableFuture<TeleportPreparationResult> prepared() {
            return this.prepared;
        }

        @NotNull
        @Override
        public CompletableFuture<TeleportResult> result() {
            return this.result;
        }

        // 启动传送. 先挂上条件监听, 再到异步线程上取目标服务器 ID, 拿到后开始准备.
        private void start(Supplier<CompletionStage<String>> server) {
            // 已经完成的条件会立刻回调, 已经失败的条件会让传送在准备之前就结束
            for (int i = 0, size = this.conditions.size(); i < size; i++) {
                this.conditions.get(i).whenComplete((ignored, error) -> this.onCondition(error));
            }
            // 目标服务器 ID 可能来自调用方的 Supplier, 放到异步线程上求值
            CompletableFuture.completedFuture(null)
                    .thenComposeAsync(ignored -> server.get(), TeleportSource.this.plugin.scheduler().async())
                    .whenComplete((target, error) -> {
                        if (error != null) {
                            this.finish(null, ThrowableUtils.unwrap(error));
                        } else {
                            this.prepare(target);
                        }
                    });
        }

        // 准备阶段. 目标服就是本服时直接调用 {@link TeleportTarget#acceptTeleportPrepare}, 否则通过消息代理发出准备请求.
        private void prepare(String target) {
            // 传送可能在拿到服务器 ID 之前就已经结束, 那就不必再发请求
            synchronized (this) {
                if (this.state != State.PREPARING) return;
                this.server = target;
            }
            TeleportPrepareRequest request = new TeleportPrepareRequest(this.requestId, this.context, this.position, this.targetProcessors);
            request.setSourceServer(TeleportSource.this.serverId);
            request.setTargetServer(target);
            // 本服准备, 不经过消息代理
            if (target.equals(TeleportSource.this.serverId)) {
                TeleportSource.this.plugin.teleportService().target().acceptTeleportPrepare(request).whenComplete((outcome, error) -> {
                    if (error != null) {
                        this.finish(null, ThrowableUtils.unwrap(error));
                        return;
                    }
                    switch (outcome) {
                        case TeleportPosition.Result.Success success -> this.ready(success.location(), request.context());
                        case TeleportPosition.Result.Fail fail -> this.finish(new TeleportResult.Fail(fail.reason()), null);
                    }
                });
                return;
            }
            // 跨服准备. 目标服不在线时消息代理 10 秒后超时, 按目标服无响应失败
            TeleportSource.this.plugin.messageBrokerManager().broker()
                    .publishTwoWay(request, target)
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            this.fail(error, MessageConstants.TELEPORT_TARGET_UNREACHABLE);
                        } else if (response.successful()) {
                            this.ready(response.location(), response.context());
                        } else {
                            this.finish(new TeleportResult.Fail(response.reason()), null);
                        }
                    });
        }

        // 准备成功, 进入等待阶段. 记下坐标和目标服带回的上下文, 完成 prepared; 条件已经齐了就直接执行; 传送已经结束时, 迟到的准备结果直接丢弃.
        private void ready(WorldLocation location, TeleportContext context) {
            String server;
            synchronized (this) {
                if (this.state != State.PREPARING) return;
                this.state = State.WAITING;
                this.location = location;
                this.context = context;
                server = this.server;
            }
            this.prepared.complete(new TeleportPreparationResult.Success(server, location));
            this.tryExecute();
        }

        // 一个执行条件完成时调用. 条件异常完成或被取消时以 Aborted 结束传送; 正常完成时计数减一, 归零时就可能开始执行.
        private void onCondition(@Nullable Throwable error) {
            if (error != null) {
                this.finish(new TeleportResult.Aborted(ThrowableUtils.unwrap(error)), null);
                return;
            }
            synchronized (this) {
                this.waiting--;
            }
            this.tryExecute();
        }

        // 准备已经完成, 条件也全部满足时开始执行. 同一玩家同时只能有一个传送在执行, 已经有时以 TELEPORT_BUSY 结束.
        private void tryExecute() {
            // 准备完成和最后一个条件完成可能从两个线程同时走到这里, 锁里只放其中一次进入执行
            boolean busy;
            synchronized (this) {
                if (this.state != State.WAITING || this.waiting > 0) return;
                busy = TeleportSource.this.executions.putIfAbsent(this.player.uniqueId(), this) != null;
                if (!busy) {
                    this.state = State.EXECUTING;
                }
            }
            if (busy) {
                this.finish(new TeleportResult.Fail(MessageConstants.TELEPORT_BUSY), null);
            } else if (this.server.equals(TeleportSource.this.serverId)) {
                this.executeLocal();
            } else {
                this.executeRemote();
            }
        }

        // 本服执行. 在异步线程上调用 teleportAsync, 超过 LOCAL_EXECUTION_SECONDS 按执行超时失败; 成功后在本服跑后处理器.
        private void executeLocal() {
            CompletableFuture.completedFuture(this.location)
                    .thenComposeAsync(location -> {
                        // 把玩家移动到目的地.
                        Location target = location.resolve();
                        if (target == null) return CompletableFuture.completedFuture(new TeleportResult.Fail(MessageConstants.TELEPORT_REJECTED_INVALID));
                        return this.player.platformPlayer.teleportAsync(target, TeleportCause.PLUGIN)
                                .thenApply(moved -> moved
                                        ? new TeleportResult.Success()
                                        : new TeleportResult.Fail(MessageConstants.TELEPORT_FAILED)
                                );
                    }, TeleportSource.this.plugin.scheduler().async())
                    .orTimeout(LOCAL_EXECUTION_SECONDS, TimeUnit.SECONDS)
                    // finish 返回 true 才说明成功是这一次给出的, 后处理器只在这时跑一次
                    .whenComplete((outcome, error) -> {
                        if (error != null) {
                            this.fail(error, MessageConstants.TELEPORT_EXECUTION_TIMEOUT);
                        } else if (this.finish(outcome, null) && outcome instanceof TeleportResult.Success) {
                            TeleportSource.this.plugin.teleportService().target().runPost(this.player, this.context, this.postProcessors);
                        }
                    });
        }

        // 跨服执行. 把上下文, 坐标和后处理器发给目标服, 以目标服回复的结果结束传送.
        private void executeRemote() {
            TeleportSource.this.plugin.messageBrokerManager().broker()
                    .publishTwoWay(new TeleportExecuteRequest(this.requestId, this.context, this.location, this.postProcessors), this.server)
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            this.fail(error, MessageConstants.TELEPORT_TARGET_UNREACHABLE);
                        } else {
                            this.finish(response.result(), null);
                        }
                    });
        }

        @Override
        public boolean cancel() {
            // 状态判断和切换在同一把锁里, 不会和刚开始的执行撞在一起
            synchronized (this) {
                if (this.state == State.EXECUTING || this.state == State.FINISHED) return false;
                this.state = State.FINISHED;
            }
            this.complete(new TeleportResult.Fail(MessageConstants.TELEPORT_CANCELLED), null);
            return true;
        }

        /**
         * 把异步操作的异常转成传送结果. 超时是可预期的失败, 以给定原因 Fail 结束; 其他异常按程序错误结束.
         *
         * @param error 回调收到的异常
         * @param timeout 超时时使用的失败原因
         */
        private void fail(Throwable error, Component timeout) {
            Throwable cause = ThrowableUtils.unwrap(error);
            if (cause instanceof TimeoutException) {
                this.finish(new TeleportResult.Fail(timeout), null);
            } else {
                this.finish(null, cause);
            }
        }

        /**
         * 结束传送. 只有第一次调用生效, 之后的调用直接返回.
         *
         * @param outcome 传送结果, 以异常结束时为 null
         * @param error 程序异常, 正常结束时为 null
         * @return 这次调用结束了传送时返回 true, 传送早已结束时返回 false
         */
        private boolean finish(@Nullable TeleportResult outcome, @Nullable Throwable error) {
            synchronized (this) {
                if (this.state == State.FINISHED) return false;
                this.state = State.FINISHED;
            }
            this.complete(outcome, error);
            return true;
        }

        /**
         * 结束后的收尾. 移出登记, 取消条件, 完成 prepared 和 result. <strong>调用前状态必须已经切到 FINISHED</strong>.
         *
         * @param outcome 传送结果, 以异常结束时为 null
         * @param error 程序异常, 正常结束时为 null
         */
        private void complete(@Nullable TeleportResult outcome, @Nullable Throwable error) {
            TeleportSource.this.submissions.remove(this.requestId, this);
            TeleportSource.this.executions.remove(this.player.uniqueId(), this);
            // 先取消条件再完成结果, 监听结果的一方看到的条件都已经落定
            cancelAll(this.conditions);
            if (error != null) {
                this.prepared.completeExceptionally(error);
                this.result.completeExceptionally(error);
            } else {
                // prepared 可能还没完成, 按结束原因补上. 成功时它一定已经完成
                switch (outcome) {
                    case TeleportResult.Success ignored -> {
                    }
                    case TeleportResult.Fail fail -> this.prepared.complete(new TeleportPreparationResult.Fail(fail.reason()));
                    case TeleportResult.Aborted ignored -> this.prepared.complete(new TeleportPreparationResult.Fail(MessageConstants.TELEPORT_CANCELLED));
                }
                this.result.complete(outcome);
            }
        }
    }

    /**
     * 提交时就被拒绝的传送. 准备结果和最终结果在创建时就已经是 Fail.
     *
     * @param requestId 随机生成的请求 ID
     * @param prepared 已经完成的准备结果
     * @param result 已经完成的最终结果
     */
    private record Rejected(
            @NotNull UUID requestId,
            @NotNull CompletableFuture<TeleportPreparationResult> prepared,
            @NotNull CompletableFuture<TeleportResult> result
    ) implements TeleportSubmission {

        @Override
        public boolean cancel() {
            return false;
        }
    }
}
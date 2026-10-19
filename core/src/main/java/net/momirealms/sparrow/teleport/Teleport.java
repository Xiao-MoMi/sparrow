package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.position.Fixed;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 传送的构建器, 也是整个传送 API 的入口.
 *
 * <p>用 {@link #builder()} 拿到构建器后, 设置目标服务器和目的地, 按需挂上处理器, 上下文和执行条件,
 * 最后选一种方式提交. 三种提交方式的区别在于调用方能拿到多少信息.
 * <ul>
 *     <li>{@link #commit} 返回最终结果, 大多数调用方用它就够了.</li>
 *     <li>{@link #commitForget} 发起后不再理会结果, 只把程序异常打印到控制台.</li>
 *     <li>{@link #submit} 返回 {@link TeleportSubmission}, 可以额外观察准备结果, 或者在执行前取消.</li>
 * </ul>
 *
 * <pre>{@code
 * Teleport.builder()
 *         .server("lobby")
 *         .position("world", 0.5, 64, 0.5)
 *         .postProcessor(new PlaySound())
 *         .commit(sparrowPlayer)
 *         .thenAccept(result -> {
 *             if (result instanceof TeleportResult.Fail fail) sparrowPlayer.sendMessage(fail.reason());
 *         });
 * }</pre>
 *
 * <p><strong>一个构建器只提交一次.</strong> 提交时上下文对象和执行条件会直接交给这次传送使用.
 */
public final class Teleport {
    private @Nullable Supplier<CompletionStage<String>> server; // 目标服务器 ID 的来源, 提交后才求值
    private @Nullable TeleportPosition position;                 // 目的地, 在目标服上解析成坐标
    private @Nullable TeleportContext context;                   // 调用方准备的上下文, 没设置时提交时新建
    private final List<PositionProcessor> targetProcessors = new ArrayList<>(); // 在目标服上依次检查或调整目的地
    private final List<PostProcessor> postProcessors = new ArrayList<>();       // 玩家到达后在落地的服务器上依次执行
    private final List<CompletionStage<?>> conditions = new ArrayList<>();      // 执行前必须全部正常完成的条件

    private Teleport() {
    }

    @NotNull
    public static Teleport builder() {
        return new Teleport();
    }

    /**
     * 把传送发往固定的服务器.
     *
     * @param server 目标服务器 ID, 与本服 ID 相同时走本服传送, 不经过消息代理
     * @return 此构建器
     */
    @NotNull
    public Teleport server(@NotNull String server) {
        this.server = () -> CompletableFuture.completedFuture(server);
        return this;
    }

    @NotNull
    public Teleport server(@NotNull Supplier<String> server) {
        this.server = () -> CompletableFuture.completedFuture(server.get());
        return this;
    }

    @NotNull
    public Teleport server(@NotNull CompletionStage<String> server) {
        this.server = () -> server;
        return this;
    }

    /**
     * 以固定坐标作为目的地, 朝向为 0. 效果等同于 {@code position(world, x, y, z, 0, 0)}.
     *
     * @param world 世界名
     * @param x 目的地 X 坐标
     * @param y 目的地 Y 坐标, 也就是玩家脚底的高度
     * @param z 目的地 Z 坐标
     * @return 此构建器
     */
    @NotNull
    public Teleport position(@NotNull String world, double x, double y, double z) {
        return this.position(world, x, y, z, 0.0F, 0.0F);
    }

    @NotNull
    public Teleport position(@NotNull String world, double x, double y, double z, float yaw, float pitch) {
        return this.position(new Fixed(new WorldLocation(world, x, y, z, yaw, pitch)));
    }

    @NotNull
    public Teleport position(@NotNull TeleportPosition position) {
        this.position = position;
        return this;
    }

    /**
     * 设置传送上下文.
     * 上下文会跟着请求发往目标服, 处理器和目标服的事件监听都能读到它. 提交时会写入玩家的 UUID 和名字.
     *
     * @param context 传送上下文, 提交后归这次传送使用
     * @return 此构建器
     */
    @NotNull
    public Teleport context(@NotNull TeleportContext context) {
        this.context = context;
        return this;
    }

    /**
     * 追加一个目标处理器.
     * 目标处理器在目标服上按添加顺序检查目的地, 每一个都可以放行, 拒绝或换一个坐标.
     *
     * @param processor 目标处理器, 类型必须在 {@link PositionProcessor#TYPES} 注册过, 目标服才能解码
     * @return 此构建器
     */
    @NotNull
    public Teleport targetProcessor(@NotNull PositionProcessor processor) {
        this.targetProcessors.add(processor);
        return this;
    }

    /**
     * 追加一个后处理器.
     * 玩家到达后, 后处理器在落地的服务器上按添加顺序执行, 比如播放音效, 给几秒无敌.
     *
     * @param processor 后处理器, 类型必须在 {@link PostProcessor#TYPES} 注册过
     * @return 此构建器
     */
    @NotNull
    public Teleport postProcessor(@NotNull PostProcessor processor) {
        this.postProcessors.add(processor);
        return this;
    }

    /**
     * 追加一个执行条件.
     * 准备完成, 并且所有条件都正常完成后, 传送才开始执行.
     *
     * <p>任一条件异常完成或被取消时, 传送以 {@link TeleportResult.Aborted} 结束, 其余条件随之被取消.
     * 传送因为别的原因先结束时, 还没完成的条件同样会被取消. 所以条件的提供方要留意自己的 future,
     * 它被外部完成时就停下手头的工作.
     *
     * @param condition 执行条件, <strong>每个条件只能交给一次传送</strong>
     * @return 此构建器
     */
    @NotNull
    public Teleport condition(@NotNull CompletionStage<?> condition) {
        this.conditions.add(condition);
        return this;
    }

    /**
     * 提交传送, 只关心最终结果.
     *
     * @param player 要传送的玩家, 不在本服在线时结果直接是 {@code Fail(TELEPORT_UNAVAILABLE)}
     * @return 最终结果, 等同于 {@code submit(player).result()}
     */
    @NotNull
    public CompletableFuture<TeleportResult> commit(@NotNull Player player) {
        return this.submit(player).result();
    }

    @NotNull
    public CompletableFuture<TeleportResult> commit(@NotNull SparrowPlayer player) {
        return this.submit(player).result();
    }

    /**
     * 提交传送后不再理会结果. 业务上的失败, 比如目标服下线, 都静默结束.
     *
     * @param player 要传送的玩家
     */
    public void commitForget(@NotNull Player player) {
        SparrowPlugin.instance().teleportService().forget(this.submit(player), player.getUniqueId());
    }

    public void commitForget(@NotNull SparrowPlayer player) {
        SparrowPlugin.instance().teleportService().forget(this.submit(player), player.uniqueId());
    }

    /**
     * 提交传送并返回句柄. 句柄可以观察准备结果和最终结果, 也可以在执行开始前取消传送.
     *
     * @param player 要传送的玩家, 不在本服在线时返回一个已经失败的句柄
     * @return 这次传送的句柄
     * @throws IllegalStateException 没有设置目标服务器或目的地时
     */
    @NotNull
    public TeleportSubmission submit(@NotNull Player player) {
        return SparrowPlugin.instance().teleportService().submit(this, player);
    }

    @NotNull
    public TeleportSubmission submit(@NotNull SparrowPlayer player) {
        return SparrowPlugin.instance().teleportService().submit(this, player);
    }

    @NotNull
    Supplier<CompletionStage<String>> getServer() {
        if (this.server == null) throw new IllegalStateException("Teleport server is not specified");
        return this.server;
    }

    @NotNull
    TeleportPosition getPosition() {
        if (this.position == null) throw new IllegalStateException("Teleport position is not specified");
        return this.position;
    }

    @NotNull
    List<PositionProcessor> targetProcessors() {
        return this.targetProcessors;
    }

    @NotNull
    List<PostProcessor> postProcessors() {
        return this.postProcessors;
    }

    @NotNull
    List<CompletionStage<?>> conditions() {
        return this.conditions;
    }

    /**
     * 生成这次传送使用的上下文.
     * 调用方设置过上下文时沿用同一个对象, 否则新建一个; 两种情况都会写入玩家的 UUID 和名字.
     *
     * @param player 要传送的玩家
     * @return 已经写入玩家信息的上下文
     */
    @NotNull
    TeleportContext createContext(@NotNull BukkitSparrowPlayer player) {
        TeleportContext current = this.context == null ? new TeleportContext() : this.context;
        current.bindPlayer(player);
        return current;
    }
}
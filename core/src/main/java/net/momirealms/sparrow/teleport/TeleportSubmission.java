package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.ThrowableUtils;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface TeleportSubmission {

    /**
     * 返回这次传送的请求 ID.
     *
     * @return 请求 ID
     */
    @NotNull
    UUID requestId();

    /**
     * 返回准备阶段的结果, 准备结束时完成.
     *
     * @return 准备结果的 future
     */
    @NotNull
    CompletableFuture<TeleportPreparationResult> preparationResult();

    /**
     * 返回传送的最终结果, 出现程序错误时以异常完成, 其余情况都以 {@link TeleportResult} 正常完成.
     *
     * @return 最终结果的 future, <strong>只用于观察, 调用方不得 complete 或 cancel 它</strong>
     */
    @NotNull
    CompletableFuture<TeleportResult> result();

    @NotNull
    default TeleportSubmission onResult(@NotNull Consumer<TeleportResult> action) {
        this.result().whenComplete((result, error) -> {
            if (error == null) {
                try {
                    action.accept(result);
                } catch (Throwable t) {
                    SparrowPlugin.instance().logger().warn("Teleport " + this.requestId() + " onResult callback failed", t);
                }
            }
        });
        return this;
    }

    @NotNull
    default TeleportSubmission onPrepared(@NotNull Consumer<TeleportPreparationResult.Success> action) {
        this.preparationResult().whenComplete((prepared, error) -> {
            if (error == null && prepared instanceof TeleportPreparationResult.Success success) {
                try {
                    action.accept(success);
                } catch (Throwable t) {
                    SparrowPlugin.instance().logger().warn("Teleport " + this.requestId() + " onPrepared callback failed", t);
                }
            }
        });
        return this;
    }

    @NotNull
    default TeleportSubmission onError(@NotNull Consumer<Throwable> action) {
        this.result().whenComplete((result, error) -> {
            if (error != null) {
                Throwable unwrap = ThrowableUtils.unwrap(error);
                try {
                    action.accept(unwrap);
                } catch (Throwable t) {
                    SparrowPlugin.instance().logger().warn("Teleport " + this.requestId() + " onError callback failed", t);
                }
            }
        });
        return this;
    }

    /**
     * 在执行开始前取消传送, 最终结果为 {@code Fail(TELEPORT_CANCELLED)}.
     * 已经发往目标服的准备请求收不回来, 目标服算完的结果回到源服后直接丢弃.
     *
     * @return 取消成功时返回 {@code true}; 传送已经开始执行或已经结束时返回 {@code false}
     */
    boolean cancel();
}
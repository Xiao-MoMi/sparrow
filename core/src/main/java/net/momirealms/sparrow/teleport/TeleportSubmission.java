package net.momirealms.sparrow.teleport;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    CompletableFuture<TeleportPreparationResult> prepared();

    /**
     * 返回传送的最终结果, 出现程序错误时以异常完成, 其余情况都以 {@link TeleportResult} 正常完成.
     *
     * @return 最终结果的 future, <strong>只用于观察, 调用方不得 complete 或 cancel 它</strong>
     */
    @NotNull
    CompletableFuture<TeleportResult> result();

    /**
     * 在执行开始前取消传送, 最终结果为 {@code Fail(TELEPORT_CANCELLED)}.
     * 已经发往目标服的准备请求收不回来, 目标服算完的结果回到源服后直接丢弃.
     *
     * @return 取消成功时返回 {@code true}; 传送已经开始执行或已经结束时返回 {@code false}
     */
    boolean cancel();
}
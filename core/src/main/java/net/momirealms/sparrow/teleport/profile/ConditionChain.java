package net.momirealms.sparrow.teleport.profile;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.teleport.condition.ConditionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

// 前一个条件正常完成才生成下一个; 整体被取消时, 一并取消正在运行的那个
final class ConditionChain {
    private final CompletableFuture<Void> result = new CompletableFuture<>();
    private final Iterator<ConditionFactory> remaining; // 只在上一个条件完成后推进, 不会并发访问
    private final BukkitSparrowPlayer player;
    private volatile @Nullable CompletableFuture<?> current;

    ConditionChain(@NotNull Iterator<ConditionFactory> remaining, @NotNull BukkitSparrowPlayer player) {
        this.remaining = remaining;
        this.player = player;
        this.result.whenComplete((ignored, error) -> {
            CompletableFuture<?> running = this.current;
            if (running != null) running.cancel(false);
        });
    }

    @NotNull
    CompletableFuture<Void> start() {
        this.next();
        return this.result;
    }

    private void next() {
        if (!this.remaining.hasNext()) {
            this.result.complete(null);
            return;
        }
        CompletableFuture<?> condition = this.remaining.next().create(this.player);
        this.current = condition;
        // 生成期间整体可能已经结束, 这时取消回调看不到它, 要在这里补上
        if (this.result.isDone()) {
            condition.cancel(false);
            return;
        }
        condition.whenComplete((ignored, error) -> {
            if (error != null) {
                this.result.completeExceptionally(error);
            } else {
                this.next();
            }
        });
    }
}
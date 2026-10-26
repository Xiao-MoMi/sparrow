package net.momirealms.sparrow.feature.back;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class BackOperations {
    private final Map<UUID, Set<CompletableFuture<?>>> active = new HashMap<>();

    <T> CompletableFuture<T> submit(UUID player, Supplier<CompletableFuture<T>> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        synchronized (this) {
            this.active.computeIfAbsent(player, ignored -> new HashSet<>()).add(result);
        }
        CompletableFuture.completedFuture(null).thenCompose(ignored -> action.get()).whenComplete((value, error) -> {
            if (error == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(error);
            }
            synchronized (this) {
                Set<CompletableFuture<?>> operations = this.active.get(player);
                operations.remove(result);
                if (operations.isEmpty()) this.active.remove(player);
            }
        });
        return result.copy();
    }

    synchronized CompletableFuture<Void> await(UUID player) {
        Set<CompletableFuture<?>> operations = this.active.get(player);
        return operations == null ? CompletableFuture.completedFuture(null) : waitFor(operations);
    }

    synchronized CompletableFuture<Void> awaitAll() {
        Set<CompletableFuture<?>> operations = new HashSet<>();
        this.active.values().forEach(operations::addAll);
        return waitFor(operations);
    }

    private static CompletableFuture<Void> waitFor(Set<CompletableFuture<?>> operations) {
        // 每次操作的异常由原调用边界报告, 会话收尾只等待已接受的 I/O 结束
        return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new)).handle((ignored, error) -> null);
    }
}
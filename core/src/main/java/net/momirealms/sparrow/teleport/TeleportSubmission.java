package net.momirealms.sparrow.teleport;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TeleportSubmission {

    @NotNull
    UUID requestId();

    @NotNull
    CompletableFuture<TeleportPreparationResult> result();

    boolean cancel();
}
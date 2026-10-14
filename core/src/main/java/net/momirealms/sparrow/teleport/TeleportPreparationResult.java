package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public sealed interface TeleportPreparationResult {

    non-sealed interface Success extends TeleportPreparationResult {

        @NotNull
        String server();

        @NotNull
        WorldLocation location();

        @NotNull
        TeleportContext context();

        @NotNull
        CompletableFuture<TeleportResult> accept();
    }

    record Fail(@NotNull Component reason) implements TeleportPreparationResult {
    }
}
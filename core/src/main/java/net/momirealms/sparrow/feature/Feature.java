package net.momirealms.sparrow.feature;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface Feature {

    @NotNull
    String id();

    default boolean supportsHotEnable() {
        return false;
    }

    default boolean supportsHotDisable() {
        return false;
    }

    default void onPluginLoad() {
    }

    @NotNull
    CompletableFuture<Void> onEnable();

    @NotNull
    default CompletableFuture<Void> onReload() {
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    CompletableFuture<Void> onDisable();

    @NotNull
    default CompletableFuture<Void> onPluginShutdown() {
        return CompletableFuture.completedFuture(null);
    }
}
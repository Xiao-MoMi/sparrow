package net.momirealms.sparrow.player.listener;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface PlayerQuitListener {

    @NotNull
    CompletableFuture<Void> onQuit(@NotNull BukkitSparrowPlayer player);
}
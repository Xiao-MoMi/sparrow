package net.momirealms.sparrow.teleport.condition;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ConditionFactory {

    @NotNull
    CompletableFuture<?> create(@NotNull BukkitSparrowPlayer player);
}
package net.momirealms.sparrow.teleport.profile;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ConditionFactory {

    @NotNull
    CompletionStage<?> create(@NotNull BukkitSparrowPlayer player);
}
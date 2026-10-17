package net.momirealms.sparrow.player.listener;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface PlayerQuitListener {

    void onQuit(@NotNull BukkitSparrowPlayer player);
}
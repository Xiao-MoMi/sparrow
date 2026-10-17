package net.momirealms.sparrow.player.listener;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface PlayerJoinListener {

    void onJoin(@NotNull BukkitSparrowPlayer player);
}
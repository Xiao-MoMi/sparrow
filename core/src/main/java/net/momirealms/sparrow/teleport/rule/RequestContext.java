package net.momirealms.sparrow.teleport.rule;

import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RequestContext(@NotNull SparrowPlayer player, boolean self, @NotNull String world, @Nullable WorldLocation origin) {
}
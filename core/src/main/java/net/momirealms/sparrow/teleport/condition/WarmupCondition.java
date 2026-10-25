package net.momirealms.sparrow.teleport.condition;

import net.momirealms.sparrow.service.countdown.CountdownSettings;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public record WarmupCondition(@NotNull CountdownSettings settings) implements ConditionFactory {

    @NotNull
    @Override
    public CompletableFuture<Void> create(@NotNull BukkitSparrowPlayer player) {
        return SparrowPlugin.instance().countdownService().start(player, this.settings, MessageConstants.TELEPORT_WARMUP);
    }
}
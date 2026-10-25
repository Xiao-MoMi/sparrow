package net.momirealms.sparrow.teleport.condition;

import net.momirealms.sparrow.service.cooldown.CooldownActiveException;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public record CooldownCondition(@NotNull String id) implements ConditionFactory {

    @NotNull
    @Override
    public CompletableFuture<Void> create(@NotNull BukkitSparrowPlayer player) {
        return SparrowPlugin.instance().cooldownService()
                .remaining(player.uniqueId(), this.id)
                .thenAccept(remaining -> {
                    if (!remaining.isZero()) throw new CooldownActiveException(remaining);
                });
    }
}
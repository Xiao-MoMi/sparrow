package net.momirealms.sparrow.feature.deathmessages;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

record DeathMessagesConfig(
        boolean broadcast,
        @NotNull Set<String> disabledWorlds,
        @NotNull AntiSpam antiSpam,
        @NotNull String format,
        @NotNull Display display,
        @NotNull List<String> fallback,
        @NotNull Map<String, Rule> rules
) {

    record AntiSpam(boolean enabled, long intervalMillis, int maxMessages, boolean showToVictim) {
    }

    record Display(@NotNull String playerName, @NotNull List<String> playerHover, @NotNull String mobName) {
    }

    record Rule(@NotNull String id, boolean broadcast, @NotNull Set<String> disabledWorlds, @NotNull List<String> messages) {
    }
}
package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public sealed interface TeleportResult {

    record Success() implements TeleportResult {
    }

    record Fail(@NotNull Component reason) implements TeleportResult {
    }
}
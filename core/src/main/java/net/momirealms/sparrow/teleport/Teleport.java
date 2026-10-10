package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record Teleport(@NotNull UUID player, @NotNull TeleportType type, @NotNull String server, @NotNull WorldLocation location, boolean self) {
}

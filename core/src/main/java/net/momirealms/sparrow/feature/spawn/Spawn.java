package net.momirealms.sparrow.feature.spawn;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

public record Spawn(@NotNull String server, @NotNull WorldLocation location) {
}
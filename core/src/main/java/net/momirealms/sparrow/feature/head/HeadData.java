package net.momirealms.sparrow.feature.head;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record HeadData(@NotNull UUID uuid, @NotNull String name, @NotNull String texture, @Nullable String signature) {
}

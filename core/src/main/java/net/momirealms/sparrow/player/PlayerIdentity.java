package net.momirealms.sparrow.player;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PlayerIdentity(@NotNull UUID uuid, @NotNull String name) {
}
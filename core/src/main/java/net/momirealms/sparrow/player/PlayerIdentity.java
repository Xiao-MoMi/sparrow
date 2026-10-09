package net.momirealms.sparrow.player;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 一名玩家的 UUID 和名字.
 */
public record PlayerIdentity(@NotNull UUID uuid, @NotNull String name) {
}

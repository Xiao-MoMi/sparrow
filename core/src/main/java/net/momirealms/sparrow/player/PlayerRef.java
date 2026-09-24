package net.momirealms.sparrow.player;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 一名玩家的 UUID 和名字, 玩家可能在线也可能离线.
 */
public record PlayerRef(@NotNull UUID uuid, @NotNull String name) {
}

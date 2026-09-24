package net.momirealms.sparrow.player;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 全服在线名单中的一名玩家, 包含其所在服务器的标识.
 */
public record NetworkPlayer(@NotNull UUID uuid, @NotNull String name, @NotNull String server) {
}

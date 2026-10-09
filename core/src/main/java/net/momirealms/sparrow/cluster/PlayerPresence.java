package net.momirealms.sparrow.cluster;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 集群在线名单中的一名玩家, 包含其所在服务器的标识.
 */
public record PlayerPresence(@NotNull UUID uuid, @NotNull String name, @NotNull String server) {
}
package net.momirealms.sparrow.player.cluster;

import net.momirealms.sparrow.player.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 集群在线名单中的一名玩家, 包含其所在服务器的标识.
 */
public record ClusterPlayer(@NotNull UUID uuid, @NotNull String name, @NotNull String server) {

    @NotNull
    public PlayerRef ref() {
        return new PlayerRef(this.uuid, this.name);
    }
}

package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public sealed interface TeleportDestination {

    // 在落点所在的服务器上调用, 算不出落点时返回 null
    @Nullable
    WorldLocation resolve();

    record Fixed(@NotNull WorldLocation location) implements TeleportDestination {

        @NotNull
        @Override
        public WorldLocation resolve() {
            return this.location;
        }
    }

    // 落在这名玩家此刻所在的位置, 玩家不在本服时没有落点
    record OfPlayer(@NotNull UUID player) implements TeleportDestination {

        @Nullable
        @Override
        public WorldLocation resolve() {
            BukkitSparrowPlayer player = SparrowPlugin.instance().playerManager().getPlayer(this.player);
            return player == null ? null : WorldLocation.from(player.platformPlayer().getLocation());
        }
    }
}
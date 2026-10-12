package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.search.RandomTeleportArea;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public sealed interface TeleportDestination {

    // 在目标服完成准入, 选点和候选校验, 返回最终位置或拒绝原因
    @NotNull
    CompletableFuture<Result> resolve(@NotNull DestinationContext context);

    record Result(@Nullable WorldLocation location, @Nullable Component denial) {
    }



    record Fixed(@NotNull WorldLocation location) implements TeleportDestination {

        @NotNull
        @Override
        public CompletableFuture<Result> resolve(@NotNull DestinationContext context) {
            return context.resolvePoint(this.location);
        }
    }

    record RTP(@NotNull RandomTeleportArea area) implements TeleportDestination {

        @NotNull
        @Override
        public CompletableFuture<Result> resolve(@NotNull DestinationContext context) {
            return context.resolveRandom(this.area);
        }
    }

    // 落在这名玩家此刻所在的位置, 玩家不在本服时没有落点
    record OfPlayer(@NotNull UUID player) implements TeleportDestination {

        @NotNull
        @Override
        public CompletableFuture<Result> resolve(@NotNull DestinationContext context) {
            BukkitSparrowPlayer player = SparrowPlugin.instance().playerManager().getPlayer(this.player);
            return player == null
                    ? CompletableFuture.completedFuture(new Result(null, null))
                    : context.resolvePoint(WorldLocation.from(player.platformPlayer().getLocation()));
        }
    }
}
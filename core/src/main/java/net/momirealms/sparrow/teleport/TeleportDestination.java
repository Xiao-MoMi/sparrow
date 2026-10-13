package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.teleport.search.RandomTeleportArea;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public sealed interface TeleportDestination {

    record Fixed(@NotNull WorldLocation location) implements TeleportDestination {
    }

    record RTP(@NotNull RandomTeleportArea area) implements TeleportDestination {
    }

    record OfPlayer(@NotNull UUID player) implements TeleportDestination {
    }

}
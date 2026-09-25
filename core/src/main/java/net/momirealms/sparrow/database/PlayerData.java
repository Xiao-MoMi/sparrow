package net.momirealms.sparrow.database;

import net.momirealms.sparrow.world.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PlayerData(@NotNull UUID player,
                         @NotNull String name,
                         long lastLogin,
                         long lastLogout,
                         @Nullable String lastServer,
                         @Nullable WorldLocation lastLocation,
                         long updatedAt) {
}

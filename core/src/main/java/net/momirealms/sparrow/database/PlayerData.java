package net.momirealms.sparrow.database;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PlayerData(@NotNull UUID player,
                         @NotNull String name,
                         long lastLogin,
                         long lastLogout,
                         @Nullable String lastLogoutServer,
                         @Nullable WorldLocation lastLogoutLocation,
                         long lastDeath,
                         @Nullable String lastDeathServer,
                         @Nullable WorldLocation lastDeathLocation,
                         @Nullable String lastLoginIp,
                         long updatedAt) {
}

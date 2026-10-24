package net.momirealms.sparrow.database.model;

import net.momirealms.sparrow.util.ServerLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;

public record PlayerProfile(
        @NotNull String name,
        long loginAt,
        long logoutAt,
        @Nullable InetAddress ip,
        @Nullable ServerLocation logoutLocation
) {
}
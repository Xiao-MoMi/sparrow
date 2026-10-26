package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public record Warp(
        @NotNull UUID id,
        @NotNull String name,
        @NotNull String server,
        @NotNull WorldLocation location,
        @Nullable UUID creator,
        long createdAt,
        long updatedAt
) {
    public static final int MAX_NAME_LENGTH = 32;

    @NotNull
    public String getNameKey() {
        return key(this.name);
    }

    @NotNull
    public static String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
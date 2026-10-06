package net.momirealms.sparrow.feature.home;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public record Home(@NotNull UUID id,
                   @NotNull UUID owner,
                   @NotNull String name,
                   @NotNull String server,
                   @NotNull WorldLocation location,
                   long createdAt,
                   long updatedAt) {
    public static final int MAX_NAME_LENGTH = 32;

    @NotNull
    public String key() {
        return key(this.name);
    }

    @NotNull
    public static String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

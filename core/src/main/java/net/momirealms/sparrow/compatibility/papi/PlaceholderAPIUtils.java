package net.momirealms.sparrow.compatibility.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PlaceholderAPIUtils {
    private PlaceholderAPIUtils() {}

    @NotNull
    public static String parse(@NotNull Player player, @NotNull String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}

package net.momirealms.sparrow.compatibility;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlaceholderAPIHook {

    private PlaceholderAPIHook() {}

    @NotNull
    public static String parse(@Nullable Player player, @NotNull String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}

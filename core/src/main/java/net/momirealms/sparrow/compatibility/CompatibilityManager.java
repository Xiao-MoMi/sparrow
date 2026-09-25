package net.momirealms.sparrow.compatibility;

import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.compatibility.papi.PlaceholderAPIUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CompatibilityManager {
    private final SparrowPlugin plugin;
    private boolean hasPlaceholderAPI;

    public CompatibilityManager(SparrowPlugin plugin) {
        this.plugin = plugin;
    }

    public void onLoad() {
    }

    public void onEnable() {
    }

    public void onDelayedEnable() {
        if (this.isPluginEnabled("PlaceholderAPI")) {
            runCatchingHook(() -> this.hasPlaceholderAPI = true, "PlaceholderAPI");
        }
    }

    public boolean isPluginEnabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    public boolean hasPlugin(String plugin) {
        return this.getPlugin(plugin) != null;
    }

    @NotNull
    public String parsePlaceholders(@NotNull Player player, @NotNull String text) {
        return this.hasPlaceholderAPI ? PlaceholderAPIUtils.parse(player, text) : text;
    }

    private @Nullable Plugin getPlugin(String name) {
        return Bukkit.getPluginManager().getPlugin(name);
    }

    private void logHook(String plugin) {
        this.plugin.logger().info(TranslationManager.console(LogConstants.COMPATIBILITY_HOOKED, plugin));
    }

    private void runCatchingHook(ThrowableRunnable runnable, String plugin) {
        try {
            runnable.run();
            logHook(plugin);
        } catch (Throwable e) {
            this.plugin.logger().warn(TranslationManager.console(LogConstants.COMPATIBILITY_HOOK_FAILED, plugin), e);
        }
    }

    @FunctionalInterface
    private interface ThrowableRunnable {
        void run() throws Throwable;
    }
}

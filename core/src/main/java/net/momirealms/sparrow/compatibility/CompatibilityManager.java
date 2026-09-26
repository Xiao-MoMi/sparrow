package net.momirealms.sparrow.compatibility;

import net.kyori.adventure.util.TriState;
import net.momirealms.sparrow.compatibility.luckperms.LuckPermsHook;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.compatibility.papi.PlaceholderAPIUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class CompatibilityManager {
    private final SparrowPlugin plugin;
    private boolean hasPlaceholderAPI;
    private volatile LuckPermsHook luckPerms; // 登录线程异步读取

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
        if (this.isPluginEnabled("LuckPerms")) {
            runCatchingHook(() -> this.luckPerms = new LuckPermsHook(), "LuckPerms");
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

    /**
     * 在玩家实体创建前判断权限, 目前仅接入 LuckPerms.
     * 权限插件未设置该节点时按 Bukkit 默认规则只授予 OP. LuckPerms 在 AsyncPlayerPreLoginEvent 的 LOW 优先级加载用户.
     *
     * @param uniqueId 玩家 UUID
     * @param permission 权限节点
     * @return 是否拥有该权限
     */
    public boolean hasPermissionBeforeJoin(@NotNull UUID uniqueId, @NotNull String permission) {
        LuckPermsHook hook = this.luckPerms;
        TriState state = hook == null ? TriState.NOT_SET : hook.check(uniqueId, permission);
        if (state != TriState.NOT_SET) return state == TriState.TRUE;
        return Bukkit.getOfflinePlayer(uniqueId).isOp();
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

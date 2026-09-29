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
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public final class CompatibilityManager {
    public static final int UNLIMITED = Integer.MAX_VALUE; // 数量上限为无限时的返回值

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

    public boolean hasPlaceholderAPI() {
        return this.hasPlaceholderAPI;
    }

    @NotNull
    public String parsePlaceholders(@Nullable Player player, @NotNull String text) {
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

    /**
     * 读取在线玩家 {@code <node>.<数字>} 形式的数量上限.
     * 拥有 {@code <node>.unlimited} 时无上限.
     *
     * @param node 不带数字的节点, 例如 {@code sparrow.max-homes}
     * @return 上限, 无上限时为 {@link #UNLIMITED}
     */
    public int permissionLimit(@NotNull Player player, @NotNull String node, int defaultValue) {
        if (player.hasPermission(node + ".unlimited")) return UNLIMITED;
        String prefix = node + ".";
        int highest = -1;
        // 有 LuckPerms 时读它缓存好的权限表, 继承与上下文已经算好
        LuckPermsHook hook = this.luckPerms;
        if (hook != null) {
            for (Map.Entry<String, Boolean> entry : hook.permissionMap(player).entrySet()) {
                if (entry.getValue()) highest = Math.max(highest, suffixValue(entry.getKey(), prefix));
            }
        } else {
            for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
                if (info.getValue()) highest = Math.max(highest, suffixValue(info.getPermission(), prefix));
            }
        }
        return highest < 0 ? defaultValue : highest;
    }

    // 节点以 prefix 开头且其余部分是非负整数时返回该整数, 否则返回 -1
    private static int suffixValue(String permission, String prefix) {
        int length = permission.length();
        if (length == prefix.length() || !permission.startsWith(prefix)) return -1;
        int value = 0;
        for (int i = prefix.length(); i < length; i++) {
            int digit = permission.charAt(i) - '0';
            if (digit < 0 || digit > 9 || value > (Integer.MAX_VALUE - digit) / 10) return -1;
            value = value * 10 + digit;
        }
        return value;
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

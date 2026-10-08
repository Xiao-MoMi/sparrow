package net.momirealms.sparrow.compatibility;

import net.kyori.adventure.util.TriState;
import net.momirealms.sparrow.compatibility.luckperms.LuckPermsHook;
import net.momirealms.sparrow.locale.LogConstants;
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
            this.runCatchingHook(() -> this.hasPlaceholderAPI = true, "PlaceholderAPI");
        }
        if (this.isPluginEnabled("LuckPerms")) {
            this.runCatchingHook(() -> this.luckPerms = new LuckPermsHook(), "LuckPerms");
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
        if (state != TriState.NOT_SET) {
            return state == TriState.TRUE;
        }
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
        if (player.hasPermission(node + ".unlimited")) {
            return UNLIMITED;
        }
        int highest = this.permissionValue(player, node + ".", true);
        return highest < 0 ? defaultValue : highest;
    }

    /**
     * 读取在线玩家 {@code <node>.<数字>} 形式的数值并取最小的一个.
     *
     * @param node 不带数字的节点, 例如 {@code sparrow.teleport-warmup}
     */
    public int permissionMinimum(@NotNull Player player, @NotNull String node, int defaultValue) {
        int lowest = this.permissionValue(player, node + ".", false);
        return lowest < 0 ? defaultValue : lowest;
    }

    // 扫描 prefix 后接非负整数且值为 true 的节点, 返回其中最大或最小的数, 没有时返回 -1
    private int permissionValue(Player player, String prefix, boolean highest) {
        int result = -1;
        // 有 LuckPerms 时读它缓存好的权限表, 继承与上下文已经算好
        LuckPermsHook hook = this.luckPerms;
        if (hook != null) {
            for (Map.Entry<String, Boolean> entry : hook.permissionMap(player).entrySet()) {
                if (entry.getValue()) {
                    result = pick(result, suffixValue(entry.getKey(), prefix), highest);
                }
            }
        } else {
            for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
                if (info.getValue()) {
                    result = pick(result, suffixValue(info.getPermission(), prefix), highest);
                }
            }
        }
        return result;
    }

    // -1 表示没有值, 不参与比较
    private static int pick(int current, int candidate, boolean highest) {
        if (candidate < 0) {
            return current;
        }
        if (current < 0) {
            return candidate;
        }
        return highest ? Math.max(current, candidate) : Math.min(current, candidate);
    }

    // 节点以 prefix 开头且其余部分是非负整数时返回该整数, 否则返回 -1
    private static int suffixValue(String permission, String prefix) {
        int length = permission.length();
        if (length == prefix.length() || !permission.startsWith(prefix)) {
            return -1;
        }
        int value = 0;
        for (int i = prefix.length(); i < length; i++) {
            int digit = permission.charAt(i) - '0';
            if (digit < 0 || digit > 9 || value > (Integer.MAX_VALUE - digit) / 10) {
                return -1;
            }
            value = value * 10 + digit;
        }
        return value;
    }

    @Nullable
    private Plugin getPlugin(String name) {
        return Bukkit.getPluginManager().getPlugin(name);
    }

    private void logHook(String plugin) {
        this.plugin.logger().info(LogConstants.COMPATIBILITY_HOOKED, plugin);
    }

    private void runCatchingHook(ThrowableRunnable runnable, String plugin) {
        try {
            runnable.run();
            this.logHook(plugin);
        } catch (Throwable e) {
            this.plugin.logger().warn(LogConstants.COMPATIBILITY_HOOK_FAILED, e, plugin);
        }
    }

    @FunctionalInterface
    private interface ThrowableRunnable {

        void run() throws Throwable;
    }
}
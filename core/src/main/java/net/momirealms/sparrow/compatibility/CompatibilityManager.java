package net.momirealms.sparrow.compatibility;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.ThrowableRunnable;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CompatibilityManager {
    public static final int UNLIMITED = Integer.MAX_VALUE; // 数量上限为无限时的返回值

    private final SparrowPlugin plugin;
    private final Cache<Map<String, Boolean>, Map<String, NumericPermission[]>> permissionValueIndexes = Caffeine.newBuilder().weakKeys().build(); // 按 LP 权限表实例复用候选, 旧权限表释放后对应索引可回收
    private boolean hasPlaceholderAPI;
    private boolean hasLuckPerms;
    private volatile LuckPermsHook luckPermsHook;

    public CompatibilityManager(SparrowPlugin plugin) {
        this.plugin = plugin;
    }

    public void onLoad() {
    }

    public void onEnable() {
        if (this.hasPlugin("PlaceholderAPI")) {
            this.runCatchingHook(() -> this.hasPlaceholderAPI = true, "PlaceholderAPI");
        }
        if (this.hasPlugin("LuckPerms")) {
            this.runCatchingHook(() -> {
                this.hasLuckPerms = true;
                this.luckPermsHook = new LuckPermsHook();
            }, "LuckPerms");
        }
    }

    public boolean hasPlugin(String plugin) {
        return this.getPlugin(plugin) != null;
    }

    public boolean isPluginEnabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
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

    /** PlaceholderAPI **/

    public boolean hasPlaceholderAPI() {
        return this.hasPlaceholderAPI;
    }

    @NotNull
    public String parsePlaceholders(@NotNull Player player, @NotNull String text) {
        return this.hasPlaceholderAPI ? PlaceholderAPIHook.parse(player, text) : text;
    }

    @NotNull
    public String parsePlaceholders(@NotNull BukkitSparrowPlayer player, @NotNull String text) {
        return this.hasPlaceholderAPI ? PlaceholderAPIHook.parse(player.platformPlayer, text) : text;
    }

    /** LuckPerms **/

    @Nullable
    public LuckPermsHook luckPermsHook() {
        return this.luckPermsHook;
    }

    public boolean hasLuckPerms() {
        return this.hasLuckPerms;
    }

    // 本服在线使用当前会话权限, 其余优先读取 LP 缓存, 未加载时按本服 OP 状态判断.
    public boolean hasPermission(@NotNull UUID uniqueId, @NotNull String permission) {
        BukkitSparrowPlayer local = this.plugin.playerManager().getOnlinePlayer(uniqueId);
        if (local != null) {
            return local.platformPlayer.hasPermission(permission);
        }
        if (this.luckPermsHook != null) {
            Boolean allowed = this.luckPermsHook.getCachedPermission(uniqueId, permission);
            if (allowed != null) return allowed;
        }
        return Bukkit.getOfflinePlayer(uniqueId).isOp();
    }

    // 本服离线时允许 LP 加载用户, 未安装 LP 时按本服 OP 状态判断.
    @NotNull
    public CompletableFuture<Boolean> hasPermissionAsync(@NotNull UUID uniqueId, @NotNull String permission) {
        BukkitSparrowPlayer local = this.plugin.playerManager().getOnlinePlayer(uniqueId);
        if (local != null) {
            return CompletableFuture.completedFuture(local.platformPlayer.hasPermission(permission));
        }
        if (this.luckPermsHook != null) {
            return this.luckPermsHook.hasPermissionAsync(uniqueId, permission);
        }
        return CompletableFuture.completedFuture(Bukkit.getOfflinePlayer(uniqueId).isOp());
    }

    // 按方向检查候选, unlimited 作为正无穷参与比较, 没有授予任何值时使用默认值.
    public int getPermissionValue(@NotNull UUID uniqueId, @NotNull String node, int defaultValue, @NotNull PermissionValueMode mode) {
        boolean maximum = mode == PermissionValueMode.MAX;
        boolean unlimited = this.hasPermission(uniqueId, node + ".unlimited");
        if (maximum && unlimited) return UNLIMITED;
        LuckPermsHook hook = this.luckPermsHook;
        Map<String, Boolean> permissions = hook == null ? null : hook.getCachedPermissionNodes(uniqueId);
        if (permissions != null) {
            Map<String, NumericPermission[]> index = this.permissionValueIndexes.get(permissions, CompatibilityManager::buildPermissionIndex);
            NumericPermission[] candidates = index.get(node);
            if (candidates != null) {
                int step = maximum ? -1 : 1;
                for (int i = maximum ? candidates.length - 1 : 0; i >= 0 && i < candidates.length; i += step) {
                    NumericPermission candidate = candidates[i];
                    if (this.hasPermission(uniqueId, candidate.permission())) return candidate.value();
                }
            }
        } else {
            BukkitSparrowPlayer local = this.plugin.playerManager().getOnlinePlayer(uniqueId);
            if (local != null) {
                // Bukkit 权限附件缺少统一的变更通知, 每次读取当前有效节点.
                String prefix = node + ".";
                int result = -1;
                for (PermissionAttachmentInfo info : local.platformPlayer.getEffectivePermissions()) {
                    if (!info.getValue() || !info.getPermission().startsWith(prefix)) continue;
                    int value = suffixValue(info.getPermission(), prefix.length());
                    if (value < 0) continue;
                    if (result < 0 || (maximum ? value > result : value < result)) {
                        result = value;
                    }
                }
                if (result >= 0) return result;
            }
        }
        return unlimited ? UNLIMITED : defaultValue;
    }

    @NotNull
    public CompletableFuture<Integer> getPermissionValueAsync(@NotNull UUID uniqueId, @NotNull String node, int defaultValue, @NotNull PermissionValueMode mode) {
        LuckPermsHook hook = this.luckPermsHook;
        if (this.plugin.playerManager().getOnlinePlayer(uniqueId) != null || hook == null) {
            return CompletableFuture.completedFuture(this.getPermissionValue(uniqueId, node, defaultValue, mode));
        }
        return hook.loadUser(uniqueId).thenApply(ignored -> this.getPermissionValue(uniqueId, node, defaultValue, mode));
    }

    // 一次解析权限表中的数字节点, 每个前缀的候选按数值升序排列.
    @NotNull
    private static Map<String, NumericPermission[]> buildPermissionIndex(@NotNull Map<String, Boolean> permissions) {
        Map<String, List<NumericPermission>> groups = new HashMap<>();
        for (String permission : permissions.keySet()) {
            int separator = permission.lastIndexOf('.');
            if (separator < 0) continue;
            int value = suffixValue(permission, separator + 1);
            if (value < 0) continue;
            String node = permission.substring(0, separator);
            groups.computeIfAbsent(node, ignored -> new ArrayList<>()).add(new NumericPermission(permission, value));
        }
        Map<String, NumericPermission[]> index = new HashMap<>(groups.size());
        for (Map.Entry<String, List<NumericPermission>> group : groups.entrySet()) {
            List<NumericPermission> candidates = group.getValue();
            candidates.sort(Comparator.comparingInt(NumericPermission::value));
            index.put(group.getKey(), candidates.toArray(NumericPermission[]::new));
        }
        return Map.copyOf(index);
    }

    // 完整后缀为非负 int 时返回其值, 空后缀, 非数字和溢出均返回 -1.
    private static int suffixValue(@NotNull String permission, int start) {
        int length = permission.length();
        if (start == length) return -1;
        int value = 0;
        for (int i = start; i < length; i++) {
            int digit = permission.charAt(i) - '0';
            if (digit < 0 || digit > 9 || value > (Integer.MAX_VALUE - digit) / 10) return -1;
            value = value * 10 + digit;
        }
        return value;
    }

    public enum PermissionValueMode {
        MIN, MAX
    }

    private record NumericPermission(@NotNull String permission, int value) {
    }
}

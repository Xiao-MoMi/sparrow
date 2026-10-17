package net.momirealms.sparrow.compatibility;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsHook {
    private final LuckPerms api = LuckPermsProvider.get();

    // 查询已加载用户的权限, 使用当前可用上下文.
    @Nullable
    public Boolean getCachedPermission(@NotNull UUID uniqueId, @NotNull String permission) {
        User user = this.api.getUserManager().getUser(uniqueId);
        return user == null ? null : user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    // 查询用户权限
    @NotNull
    public CompletableFuture<Boolean> hasPermissionAsync(@NotNull UUID uniqueId, @NotNull String permission) {
        // 有缓存
        UserManager users = this.api.getUserManager();
        User cached = users.getUser(uniqueId);
        if (cached != null) {
            boolean result = cached.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
            return CompletableFuture.completedFuture(result);
        }
        // 无缓存则加载缓存
        return users.loadUser(uniqueId).thenApply(user -> user.getCachedData().getPermissionData().checkPermission(permission).asBoolean());
    }

    @Nullable
    Map<String, Boolean> getCachedPermissionNodes(@NotNull UUID uniqueId) {
        User user = this.api.getUserManager().getUser(uniqueId);
        return user == null ? null : user.getCachedData().getPermissionData().getPermissionMap();
    }

    @NotNull
    CompletableFuture<Void> loadUser(@NotNull UUID uniqueId) {
        UserManager users = this.api.getUserManager();
        if (users.getUser(uniqueId) != null) return CompletableFuture.completedFuture(null);
        return users.loadUser(uniqueId).thenApply(user -> null);
    }

    // 在线玩家在当前上下文下已解析好的全部权限节点, 包括继承来的节点.
    @NotNull
    public Map<String, Boolean> permissionMap(@NotNull Player player) {
        return this.api.getPlayerAdapter(Player.class).getPermissionData(player).getPermissionMap();
    }
}
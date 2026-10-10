package net.momirealms.sparrow.compatibility.luckperms;

import net.kyori.adventure.util.TriState;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LuckPermsHook {
    private final LuckPerms api = LuckPermsProvider.get();

    /**
     * 按静态上下文查询已加载用户的权限, 不会触发数据库读取.
     * LuckPerms 在 AsyncPlayerPreLoginEvent 的 LOW 优先级加载用户, 之后的优先级即可查询.
     *
     * @param uniqueId 玩家 UUID
     * @param permission 权限节点
     * @return 权限值, 用户未加载或未设置该节点时为 {@link TriState#NOT_SET}
     */
    @NotNull
    public TriState check(@NotNull UUID uniqueId, @NotNull String permission) {
        User user = this.api.getUserManager().getUser(uniqueId);
        if (user == null) return TriState.NOT_SET;
        return this.check(user, permission);
    }

    @NotNull
    public CompletableFuture<TriState> checkAsync(@NotNull UUID uniqueId, @NotNull String permission) {
        UserManager users = this.api.getUserManager();
        User cached = users.getUser(uniqueId);
        if (cached != null) return CompletableFuture.completedFuture(this.check(cached, permission));
        return users.loadUser(uniqueId).thenApply(user -> {
            try {
                return this.check(user, permission);
            } finally {
                // 释放本次手动加载的用户, 在线用户由 LuckPerms 保留.
                users.cleanupUser(user);
            }
        });
    }

    private TriState check(User user, String permission) {
        return switch (user.getCachedData().getPermissionData(this.api.getContextManager().getStaticQueryOptions()).checkPermission(permission)) {
            case TRUE -> TriState.TRUE;
            case FALSE -> TriState.FALSE;
            case UNDEFINED -> TriState.NOT_SET;
        };
    }

    /**
     * 在线玩家在当前上下文下已解析好的全部权限节点, 包括继承来的节点.
     *
     * @return 节点到权限值的映射, <strong>只读</strong>
     */
    @NotNull
    public Map<String, Boolean> permissionMap(@NotNull Player player) {
        return this.api.getPlayerAdapter(Player.class).getPermissionData(player).getPermissionMap();
    }
}
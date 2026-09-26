package net.momirealms.sparrow.compatibility.luckperms;

import net.kyori.adventure.util.TriState;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

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
        return switch (user.getCachedData().getPermissionData(this.api.getContextManager().getStaticQueryOptions()).checkPermission(permission)) {
            case TRUE -> TriState.TRUE;
            case FALSE -> TriState.FALSE;
            case UNDEFINED -> TriState.NOT_SET;
        };
    }
}

package net.momirealms.sparrow.plugin.command;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.permission.AndPermission;
import org.incendo.cloud.permission.OrPermission;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PermissionResult;
import org.incendo.cloud.permission.PredicatePermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 模块自带命令的额外权限, 模块未启用时不通过.
 * 命令因此在玩家的命令树中隐藏, 执行时提示模块未启用.
 *
 * @param featureId 模块 ID
 */
public record FeaturePermission(@NotNull String featureId) implements PredicatePermission<CommandSender> {

    @NotNull
    @Override
    public PermissionResult testPermission(@NotNull CommandSender sender) {
        return PermissionResult.of(SparrowPlugin.instance().featureManager().isEnabled(this.featureId), this);
    }

    @NotNull
    @Override
    public String permissionString() {
        return "feature:" + this.featureId;
    }

    /**
     * 在命令的权限里找出当前未启用的模块.
     * Cloud 拒绝执行时给出的是命令的完整组合权限, 需要逐层查找.
     *
     * @param permission 命令的权限, 可以是 {@link Permission#allOf} 等组合出的权限
     * @return 未启用的模块权限, 没有时为 null
     */
    @Nullable
    public static FeaturePermission findDisabled(@NotNull Permission permission) {
        if (permission instanceof FeaturePermission feature) {
            return SparrowPlugin.instance().featureManager().isEnabled(feature.featureId) ? null : feature;
        }
        if (!(permission instanceof AndPermission) && !(permission instanceof OrPermission)) {
            return null;
        }
        for (Permission inner : permission.permissions()) {
            FeaturePermission found = findDisabled(inner);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}

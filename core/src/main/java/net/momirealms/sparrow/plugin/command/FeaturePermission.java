package net.momirealms.sparrow.plugin.command;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.permission.AndPermission;
import org.incendo.cloud.permission.OrPermission;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PermissionResult;
import org.incendo.cloud.permission.PredicatePermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * 模块自带命令的额外权限, 模块未启用时不通过.
 * 命令因此在玩家的命令树中隐藏, 执行时提示模块未启用.
 *
 * @param featureId 模块 ID
 * @param enabled 模块当前是否启用.
 */
public record FeaturePermission(@NotNull String featureId, @NotNull BooleanSupplier enabled) implements PredicatePermission<CommandSender> {

    @Override
    @NotNull
    public PermissionResult testPermission(@NotNull CommandSender sender) {
        return PermissionResult.of(this.enabled.getAsBoolean(), this);
    }

    @Override
    @NotNull
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
        if (permission instanceof FeaturePermission feature) return feature.enabled.getAsBoolean() ? null : feature;
        if (!(permission instanceof AndPermission) && !(permission instanceof OrPermission)) return null;
        for (Permission inner : permission.permissions()) {
            FeaturePermission found = findDisabled(inner);
            if (found != null) return found;
        }
        return null;
    }
}

package net.momirealms.sparrow.player.teleport;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 一次传送的预热与冷却参数.
 * 各功能用它表达自己配置的默认值, 实际使用的参数由 {@link #resolve} 得出.
 *
 * @param warmupSeconds 原地等待的秒数, 0 表示立即传送
 * @param cooldownSeconds 该类型传送的冷却秒数, 0 表示不检查也不记录冷却
 * @param cancelOnMove 预热期间移动时是否取消
 * @param cancelOnDamage 预热期间受伤时是否取消
 */
public record TeleportOptions(@NotNull TeleportType type,
                              int warmupSeconds,
                              int cooldownSeconds,
                              boolean cancelOnMove,
                              boolean cancelOnDamage) {
    public static final String WARMUP_NODE = DependencyVersions.PROJECT_ID + ".teleport-warmup"; // 后接秒数, 覆盖功能配置的预热时间
    public static final String BYPASS_WARMUP = DependencyVersions.PROJECT_ID + ".bypass.teleport-warmup";
    public static final String BYPASS_COOLDOWN = DependencyVersions.PROJECT_ID + ".bypass.teleport-cooldown";

    /**
     * 以当前参数为默认值, 按统一规则得出本次传送的参数.
     * 只有玩家自己发起时才预热和冷却, 传送别人立即执行且不记录冷却.
     * 预热取默认值与 {@code sparrow.teleport-warmup.<秒>} 中最小的节点.
     *
     * @param player 被传送的玩家
     * @param self 是否由玩家自己发起
     */
    @NotNull
    public TeleportOptions resolve(@NotNull Player player, boolean self) {
        int warmupSeconds = self && !player.hasPermission(BYPASS_WARMUP)
                ? SparrowPlugin.instance().compatibilityManager().permissionMinimum(player, WARMUP_NODE, this.warmupSeconds)
                : 0;
        int cooldownSeconds = self && !player.hasPermission(BYPASS_COOLDOWN) ? this.cooldownSeconds : 0;
        return new TeleportOptions(this.type, warmupSeconds, cooldownSeconds, this.cancelOnMove, this.cancelOnDamage);
    }
}

package net.momirealms.sparrow.feature.playerlimit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;

// 权限插件在 LOWEST 注入权限, 之后即可直接查询玩家权限
@SuppressWarnings("deprecation")
final class SpigotPlayerLimitListener implements Listener {
    private final PlayerLimitFeature feature;

    SpigotPlayerLimitListener(@NotNull PlayerLimitFeature feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(@NotNull PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.KICK_FULL || !this.feature.enabled()) return;
        if (event.getPlayer().hasPermission(PlayerLimitFeature.BYPASS_PERMISSION)) event.allow();
    }
}

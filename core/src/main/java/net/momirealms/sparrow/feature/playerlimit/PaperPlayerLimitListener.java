package net.momirealms.sparrow.feature.playerlimit;

import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

// 登录阶段和配置阶段结束时各检查一次, 都在玩家实体创建之前
final class PaperPlayerLimitListener implements Listener {
    private final PlayerLimitFeature feature;

    PaperPlayerLimitListener(@NotNull PlayerLimitFeature feature) {
        this.feature = feature;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFullCheck(@NotNull PlayerServerFullCheckEvent event) {
        if (event.isAllowed() || !this.feature.enabled()) return;
        UUID uniqueId = event.getPlayerProfile().getId();
        if (uniqueId != null && this.feature.bypassBeforeJoin(uniqueId)) event.allow(true);
    }
}

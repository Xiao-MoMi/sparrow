package net.momirealms.sparrow.feature.rtp;

import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.service.countdown.CountdownSettings;
import net.momirealms.sparrow.teleport.position.rtp.RtpArea;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

record RtpConfig(@NotNull Options options, @NotNull List<PermissionOverride> overrides, @NotNull Map<String, Region> regions) {

    // 从上往下取第一条拥有权限的覆盖, 都没有就用原始设置
    @NotNull
    Options select(@NotNull Permissible subject) {
        for (int i = 0, size = this.overrides.size(); i < size; i++) {
            PermissionOverride override = this.overrides.get(i);
            if (subject.hasPermission(override.permission())) return override.options();
        }
        return this.options;
    }

    record Options(@NotNull Duration cooldown, @NotNull CountdownSettings warmup, @NotNull Duration invulnerability, @NotNull Sound afterSound) {
    }

    record PermissionOverride(@NotNull String permission, @NotNull Options options) {
    }

    // server 为 null 时在玩家当前所在的服务器上随机, permission 为 null 时不检查区域权限
    record Region(@NotNull String name, @Nullable String server, @Nullable String permission, @NotNull List<RtpArea> areas) {
    }
}
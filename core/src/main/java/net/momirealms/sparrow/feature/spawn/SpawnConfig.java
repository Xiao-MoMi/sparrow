package net.momirealms.sparrow.feature.spawn;

import net.momirealms.sparrow.service.countdown.CountdownSettings;
import net.momirealms.sparrow.util.ServerLocation;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

record SpawnConfig(@Nullable ServerLocation destination, Options options, List<PermissionOverride> overrides) {

    Options select(Permissible player) {
        for (int i = 0, size = this.overrides.size(); i < size; i++) {
            PermissionOverride override = this.overrides.get(i);
            if (player.hasPermission(override.permission())) return override.options();
        }
        return this.options;
    }

    record Options(Duration cooldown, CountdownSettings warmup) {
    }

    record PermissionOverride(String permission, Options options) {
    }
}
package net.momirealms.sparrow.feature.back;

import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.service.countdown.CountdownSettings;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.permissions.Permissible;

import java.time.Duration;
import java.util.List;
import java.util.Set;

record BackConfig(Policy back, Policy deathback, Set<TeleportCause> causes, long joinGraceMillis, boolean recordDeath) {

    record Policy(Options options, List<PermissionOverride> overrides) {

        Options select(Permissible subject) {
            for (int i = 0, size = this.overrides.size(); i < size; i++) {
                PermissionOverride override = this.overrides.get(i);
                if (subject.hasPermission(override.permission())) return override.options();
            }
            return this.options;
        }
    }

    record Options(Duration cooldown, CountdownSettings warmup, Duration invulnerability, Sound afterSound) {
    }

    record PermissionOverride(String permission, Options options) {
    }
}
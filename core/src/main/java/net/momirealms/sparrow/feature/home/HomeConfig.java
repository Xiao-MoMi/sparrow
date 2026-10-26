package net.momirealms.sparrow.feature.home;

import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.service.countdown.CountdownSettings;
import org.bukkit.permissions.Permissible;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

record HomeConfig(String defaultName, int maxHomes, Pattern namePattern, Policy teleportOptions) {

    boolean validName(String name) {
        return !name.isEmpty() && name.length() <= Home.MAX_NAME_LENGTH
                && name.codePoints().noneMatch(character -> Character.isWhitespace(character) || Character.isSpaceChar(character))
                && this.namePattern.matcher(name).matches();
    }

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
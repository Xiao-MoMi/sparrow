package net.momirealms.sparrow.service.countdown;

import net.kyori.adventure.sound.Sound;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public record CountdownSettings(
        @NotNull Duration duration,
        boolean cancelOnMove,
        boolean cancelOnDamage,
        @NotNull CountdownDisplay display,
        @NotNull BossEvent.BossBarColor bossBarColor,
        @NotNull BossEvent.BossBarOverlay bossBarOverlay,
        @NotNull Sound tickSound,
        @NotNull Sound cancelSound
) {
}
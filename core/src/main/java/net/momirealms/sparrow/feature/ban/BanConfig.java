package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.sound.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

record BanConfig(Map<String, String> reasonPresets, Sound notifyBanSound, Sound notifyUnbanSound) {

    @NotNull
    String getReason(String input) {
        return this.reasonPresets.getOrDefault(input, input);
    }

    @Nullable
    Sound getNotifySound(boolean banned) {
        Sound sound = banned ? this.notifyBanSound : this.notifyUnbanSound;
        return sound.volume() == 0.0F ? null : sound;
    }
}
package net.momirealms.sparrow.teleport;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SoundProcessor implements TeleportProcessor.Post {
    private Sound sound = Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 1.0f, 1.0f);

    @Override
    public void arrived(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport) {
        player.playSound(this.sound);
    }
}
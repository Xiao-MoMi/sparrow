package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SoundProcessor implements TeleportProcessor.Post {
    public static final SparrowKey TYPE = SparrowKey.sparrow("sound");

    private Sound sound = Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 1.0f, 1.0f);

    @Override
    public void arrived(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport) {
        player.playSound(this.sound);
    }
}
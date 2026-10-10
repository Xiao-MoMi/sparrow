package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SoundProcessor implements TeleportProcessor.Pre, TeleportProcessor.Post {
    public static final SparrowKey TYPE = SparrowKey.sparrow("sound");

    private Sound sound = Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 1.0f, 1.0f);

    @NotNull
    @Override
    public CompletableFuture<Component> before(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport) {
        player.playSound(this.sound);
        return PASS;
    }

    @Override
    public void arrived(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport) {
        player.playSound(this.sound);
    }
}
package net.momirealms.sparrow.teleport.processor.post;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.SparrowKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public record PlaySound(@NotNull Sound sound) implements PostProcessor {
    public static final SparrowKey TYPE = SparrowKey.sparrow("sound");

    public PlaySound() {
        this(Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 1.0F, 1.0F));
    }

    @NotNull
    public static PlaySound fromNbt(@NotNull CompoundTag tag) {
        Sound sound = Sound.sound()
                .type(Key.key(tag.getString("key")))
                .source(Sound.Source.valueOf(tag.getString("source")))
                .volume(tag.getFloat("volume"))
                .pitch(tag.getFloat("pitch"))
                .seed(tag.containsKey("seed") ? OptionalLong.of(tag.getLong("seed")) : OptionalLong.empty())
                .build();
        return new PlaySound(sound);
    }

    @NotNull
    @Override
    public SparrowKey type() {
        return TYPE;
    }

    @NotNull
    @Override
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", this.sound.name().asString());
        tag.putString("source", this.sound.source().name());
        tag.putFloat("volume", this.sound.volume());
        tag.putFloat("pitch", this.sound.pitch());
        this.sound.seed().ifPresent(seed -> tag.putLong("seed", seed));
        return tag;
    }

    @NotNull
    @Override
    public CompletableFuture<Void> process(@NotNull Player player, @NotNull TeleportContext context) {
        BukkitSparrowPlayer current = SparrowPlugin.instance().playerManager().getOnlinePlayer(player);
        if (current == null) return CompletableFuture.failedFuture(new IllegalStateException("Teleport player is no longer on this server"));
        current.playSound(this.sound);
        return CompletableFuture.completedFuture(null);
    }
}
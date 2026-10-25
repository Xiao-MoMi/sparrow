package net.momirealms.sparrow.teleport.processor.post;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.SparrowKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public record SetCooldown(@NotNull String id, @NotNull Duration duration) implements PostProcessor {
    public static final SparrowKey TYPE = SparrowKey.sparrow("cooldown");

    @NotNull
    public static SetCooldown fromNbt(@NotNull CompoundTag tag) {
        return new SetCooldown(tag.getString("id"), Duration.ofMillis(tag.getLong("duration_millis")));
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
        tag.putString("id", this.id);
        tag.putLong("duration_millis", this.duration.toMillis());
        return tag;
    }

    @NotNull
    @Override
    public CompletableFuture<Void> process(@NotNull Player player, @NotNull TeleportContext context) {
        return SparrowPlugin.instance().cooldownService().start(player.getUniqueId(), this.id, this.duration);
    }
}
package net.momirealms.sparrow.teleport.processor.post;

import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.codec.TeleportCodec;
import net.momirealms.sparrow.teleport.codec.TeleportCodecRegistry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface PostProcessor extends TeleportCodec {
    TeleportCodecRegistry<PostProcessor> TYPES = new TeleportCodecRegistry<>();

    @NotNull
    CompletableFuture<Void> process(@NotNull Player player, @NotNull TeleportContext context);
}
package net.momirealms.sparrow.teleport.position;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.codec.TeleportCodec;
import net.momirealms.sparrow.teleport.codec.TeleportCodecRegistry;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface TeleportPosition extends TeleportCodec {
    TeleportCodecRegistry<TeleportPosition> TYPES = new TeleportCodecRegistry<>() {{
        this.register(Fixed.TYPE, Fixed::fromNbt);
    }};

    @NotNull
    CompletableFuture<Result> resolve(@NotNull TeleportContext context);

    sealed interface Result {

        record Success(@NotNull WorldLocation location) implements Result {
        }

        record Fail(@NotNull Component reason) implements Result {
        }
    }
}
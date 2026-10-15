package net.momirealms.sparrow.teleport.processor.target;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.codec.TeleportCodec;
import net.momirealms.sparrow.teleport.codec.TeleportCodecRegistry;
import net.momirealms.sparrow.teleport.processor.target.safe.FindSafeLocation;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface PositionProcessor extends TeleportCodec {
    TeleportCodecRegistry<PositionProcessor> TYPES = new TeleportCodecRegistry<>() {{
        this.register(FindSafeLocation.TYPE, FindSafeLocation::fromNbt);
        this.register(GeneratedChunk.TYPE, GeneratedChunk::fromNbt);
        this.register(WorldBlacklist.TYPE, WorldBlacklist::fromNbt);
    }};

    @NotNull
    CompletableFuture<Result> process(@NotNull TeleportContext context, @NotNull WorldLocation location);

    sealed interface Result {

        enum Accept implements Result {
            INSTANCE
        }

        record Reject(@NotNull Component reason) implements Result {
        }

        record Relocate(@NotNull WorldLocation location) implements Result {
        }
    }
}
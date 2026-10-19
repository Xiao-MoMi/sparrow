package net.momirealms.sparrow.teleport.processor.target;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.codec.TeleportCodec;
import net.momirealms.sparrow.teleport.codec.TeleportCodecRegistry;
import net.momirealms.sparrow.teleport.processor.target.safe.FindSafeLoc;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface PositionProcessor extends TeleportCodec {
    TeleportCodecRegistry<PositionProcessor> TYPES = new TeleportCodecRegistry<>() {{
        this.register(GeneratedChunk.TYPE, GeneratedChunk::fromNbt);
        this.register(WorldBlacklist.TYPE, WorldBlacklist::fromNbt);
        this.register(FindSafeLoc.TYPE, FindSafeLoc::fromNbt);
    }};

    /**
     * 检查一个候选目的地. 调用发生在目标服的异步线程上.
     *
     * @param context 传送上下文, 处理器可以读写
     * @param location 经过前面处理器处理的坐标
     * @return 处理结果, 可以异步完成
     */
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
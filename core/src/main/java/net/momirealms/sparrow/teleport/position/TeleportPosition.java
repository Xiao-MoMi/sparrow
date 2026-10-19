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
        this.register(PlayerPos.TYPE, PlayerPos::fromNbt);
    }};

    /**
     * 在目标服上把位置解析成坐标.
     *
     * @param context 传送上下文
     * @return 解析结果. 目的地找不到时以 Fail 完成, 比如目标玩家不在本服
     */
    @NotNull
    CompletableFuture<Result> resolve(@NotNull TeleportContext context);

    sealed interface Result {

        record Success(@NotNull WorldLocation location) implements Result {
        }

        record Fail(@NotNull Component reason) implements Result {
        }
    }
}
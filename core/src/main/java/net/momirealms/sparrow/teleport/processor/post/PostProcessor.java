package net.momirealms.sparrow.teleport.processor.post;

import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.codec.TeleportCodec;
import net.momirealms.sparrow.teleport.codec.TeleportCodecRegistry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface PostProcessor extends TeleportCodec {
    TeleportCodecRegistry<PostProcessor> TYPES = new TeleportCodecRegistry<>() {{
        this.register(PlaySound.TYPE, PlaySound::fromNbt);
        this.register(Invulnerability.TYPE, Invulnerability::fromNbt);
    }};

    /**
     * 对刚到达的玩家执行附加动作. 调用发生在异步线程上, 要改玩家状态时由实现自己调度到玩家所在的线程.
     *
     * @param player 刚到达的玩家
     * @param context 传送上下文
     * @return 动作完成时完成, 下一个后处理器等它完成后才开始
     */
    @NotNull
    CompletableFuture<Void> process(@NotNull Player player, @NotNull TeleportContext context);
}
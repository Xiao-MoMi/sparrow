package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.teleport.search.CandidateContext;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface TeleportProcessor {
    CompletableFuture<Component> PASS = CompletableFuture.completedFuture(null);

    // 读取配置后调用, 选项不合法时抛出 IllegalArgumentException
    default void validate() {
    }

    interface CandidateRule extends TeleportProcessor {

        /**
         * 判断候选落点是否允许使用, 同一次搜索可对不同候选重复调用.
         * <p>规则<strong>只读取上下文和坐标</strong>, 拒绝原因由调用方处理, 规则不发送消息或修改传送状态.
         *
         * @return 结果 Future, 以 null 完成时放行, 非 null 表示拒绝当前候选. 异常结束搜索, 搜索取消时可取消此 Future
         */
        @NotNull
        CompletableFuture<@Nullable Component> check(@NotNull CandidateContext context, @NotNull WorldLocation location);
    }

    interface Post extends TeleportProcessor {

        // 玩家到达落点后调用
        void arrived(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport);
    }
}
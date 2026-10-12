package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface TeleportProcessor {
    CompletableFuture<Component> PASS = CompletableFuture.completedFuture(null);

    // 读取配置后调用, 选项不合法时抛出 IllegalArgumentException
    default void validate() {
    }

    interface Pre extends TeleportProcessor {

        // 出发前在玩家所在的服务器上调用. 结果为 null 时放行, 否则中止传送并把结果提示给玩家, 空组件表示不提示
        @NotNull
        CompletableFuture<@Nullable Component> before(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport);

        // 传送有了结果后调用. 跨服成功时玩家已经离开本服
        default void finished(@NotNull Teleport teleport, @NotNull TeleportResult result) {
        }
    }

    interface RequestRule extends TeleportProcessor {

        // 在目标服搜索前检查一次, null 放行, 非 null 拒绝整个请求. 上下文只读
        @NotNull
        CompletableFuture<@Nullable Component> check(@NotNull RequestContext context);
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
package net.momirealms.sparrow.player.listener;

import net.momirealms.sparrow.player.PlayerConnection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface PlayerPrepareListener {

    /** 取得玩家锁后在异步线程调用, 全部完成后玩家才能结束配置阶段; 任一失败时拒绝进服. */
    @NotNull
    CompletableFuture<Void> onPrepare(@NotNull PlayerConnection connection);

    /** 开始过准备的连接结束时调用, 正常退出时排在退出监听器之后, 全部完成后释放玩家锁. 本次准备可能失败或未执行到这个监听器. */
    @NotNull
    CompletableFuture<Void> onRelease(@NotNull PlayerConnection connection);
}
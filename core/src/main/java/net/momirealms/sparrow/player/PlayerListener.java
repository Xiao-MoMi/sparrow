package net.momirealms.sparrow.player;

import org.jetbrains.annotations.NotNull;

/**
 * 玩家进出本服的回调, 通过 {@link PlayerManager#registerListener} 注册. 均在玩家所属线程调用.
 */
public interface PlayerListener {

    /**
     * 玩家对象创建并完成进服处理后调用, 可以在此踢出玩家.
     *
     * @param player 刚进入的玩家
     */
    default void onJoin(@NotNull SparrowPlayer player) {
    }

    /**
     * 玩家对象移除前调用. 玩家重新进入配置阶段时同样会调用, 回到游戏阶段后再次触发 {@link #onJoin}.
     *
     * @param player 正在退出的玩家
     */
    default void onQuit(@NotNull SparrowPlayer player) {
    }
}

package net.momirealms.sparrow.player;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家登录与进出本服的回调, 通过 {@link PlayerManager#registerListener} 注册.
 */
public interface PlayerListener {

    default void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
    }

    default void onJoin(@NotNull SparrowPlayer player) {
    }

    default void onQuit(@NotNull SparrowPlayer player) {
    }
}
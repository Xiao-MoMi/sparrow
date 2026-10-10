package net.momirealms.sparrow.player;

import net.momirealms.sparrow.database.PlayerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 由本服会话、代理目录或持久化记录确认过的玩家.
 */
public interface SparrowPlayer {

    @NotNull
    UUID uniqueId();

    @NotNull
    String name();

    /**
     * 根据本服当前游戏会话判断是否在线.
     */
    boolean isOnlineLocally();

    /**
     * 根据代理在线目录判断是否在任一子服在线.
     */
    boolean isOnlineGlobally();

    /**
     * 返回代理目录中的当前所在服, 没有在线记录时返回 null.
     */
    @Nullable
    String getCurrentServerId();

    @NotNull
    CompletableFuture<Boolean> checkPermission(@NotNull String permission);

    @NotNull
    CompletableFuture<Optional<PlayerData>> loadData();
}
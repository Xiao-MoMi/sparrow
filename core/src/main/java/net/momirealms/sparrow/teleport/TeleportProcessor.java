package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.SparrowPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface TeleportProcessor {
    CompletableFuture<Component> PASS = CompletableFuture.completedFuture(null);

    // 读取配置后调用. 选项不合法时抛出 IllegalArgumentException, 这一项会被忽略并在控制台警告
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

    interface Target extends TeleportProcessor {

        // 落点所在的服务器在预留落点前调用, 玩家此时可能还在别的服务器上. 结果的含义与出发前相同, 可以用 teleport.location(...) 改写落点
        @NotNull
        CompletableFuture<@Nullable Component> destination(@NotNull SparrowPlayer player, @NotNull Teleport teleport);
    }

    interface Post extends TeleportProcessor {

        // 玩家到达落点后调用
        void arrived(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport);
    }
}

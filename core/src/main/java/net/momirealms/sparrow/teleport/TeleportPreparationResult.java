package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

public sealed interface TeleportPreparationResult {

    /**
     * 准备成功.
     *
     * @param server 目标服务器 ID
     * @param location 算好的目的地坐标.
     */
    record Success(@NotNull String server, @NotNull WorldLocation location) implements TeleportPreparationResult {
    }

    /**
     * 准备失败, 或者传送在准备结束前就已经结束.
     *
     * @param reason 失败原因. 传送在准备阶段被取消, 或因为执行条件失败而放弃时, 这里是 {@code TELEPORT_CANCELLED}
     */
    record Fail(@NotNull Component reason) implements TeleportPreparationResult {
    }
}
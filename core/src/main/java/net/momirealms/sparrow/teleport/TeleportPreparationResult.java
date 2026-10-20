package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public sealed interface TeleportPreparationResult {

    default TeleportPreparationResult ifSuccess(Consumer<Success> action) {
        return this;
    }

    default TeleportPreparationResult ifFail(Consumer<Fail> action) {
        return this;
    }

    /**
     * 准备成功.
     *
     * @param server 目标服务器 ID
     * @param location 算好的目的地坐标.
     */
    record Success(@NotNull String server, @NotNull WorldLocation location) implements TeleportPreparationResult {
        @Override
        public Success ifSuccess(Consumer<Success> action) {
            action.accept(this);
            return this;
        }
    }

    /**
     * 准备失败, 或者传送在准备结束前就已经结束.
     *
     * @param reason 失败原因. 传送在准备阶段被取消, 或因为执行条件失败而放弃时, 这里是 {@code TELEPORT_CANCELLED}
     */
    record Fail(@NotNull Component reason) implements TeleportPreparationResult {
        @Override
        public Fail ifFail(Consumer<Fail> action) {
            action.accept(this);
            return this;
        }
    }
}
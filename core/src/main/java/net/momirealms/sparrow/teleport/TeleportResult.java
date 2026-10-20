package net.momirealms.sparrow.teleport;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public sealed interface TeleportResult {

    @NotNull
    default TeleportResult ifSuccess(@NotNull Consumer<Success> action) {
        return this;
    }

    @NotNull
    default TeleportResult ifFail(@NotNull Consumer<Fail> action) {
        return this;
    }

    @NotNull
    default TeleportResult ifAborted(@NotNull Consumer<Aborted> action) {
        return this;
    }

    record Success() implements TeleportResult {

        @NotNull
        @Override
        public Success ifSuccess(@NotNull Consumer<Success> action) {
            action.accept(this);
            return this;
        }
    }

    record Fail(@NotNull Component reason) implements TeleportResult {

        @NotNull
        @Override
        public Fail ifFail(@NotNull Consumer<Fail> action) {
            action.accept(this);
            return this;
        }
    }

    record Aborted(@NotNull Throwable cause) implements TeleportResult {

        @NotNull
        @Override
        public Aborted ifAborted(@NotNull Consumer<Aborted> action) {
            action.accept(this);
            return this;
        }
    }
}
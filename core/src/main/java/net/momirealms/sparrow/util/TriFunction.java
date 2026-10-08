package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;
import java.util.function.Function;

@FunctionalInterface
public interface TriFunction<T, U, V, R> {

    R apply(T var1, U var2, V var3);

    default <W> TriFunction<T, U, V, W> andThen(@NotNull Function<? super R, ? extends W> after) {
        return (t, u, v) -> after.apply(this.apply(t, u, v));
    }
}
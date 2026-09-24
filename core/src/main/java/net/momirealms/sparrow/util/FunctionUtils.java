package net.momirealms.sparrow.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class FunctionUtils {
    private FunctionUtils() {}

    public static <T> T apply(T t, Consumer<T> function) {
        function.accept(t);
        return t;
    }

    public static <T> T applyIfNotNull(T t, Function<T, T> function) {
        return t == null ? null : function.apply(t);
    }

    public static <T> T applyIf(T t, Predicate<T> predicate, Function<T, T> function) {
        return predicate.test(t) ? function.apply(t) : t;
    }

    public static <T> T also(T t, Consumer<T> function) {
        function.accept(t);
        return t;
    }

    public static <T> T alsoIfNotNull(T t, Consumer<T> function) {
        if (t == null) return null;
        function.accept(t);
        return t;
    }

    public static <T> T alsoIf(T t, Predicate<T> predicate, Consumer<T> function) {
        if (predicate.test(t)) {
            function.accept(t);
        }
        return t;
    }

    public static <T, R> R with(T t, Function<T, R> function) {
        return function.apply(t);
    }

    public static <T, R> R let(T t, Function<T, R> function) {
        return function.apply(t);
    }

    public static <T, R> R letIfNotNull(T t, Function<T, R> function) {
        return t == null ? null : function.apply(t);
    }

    public static <T> T takeIf(T t, Predicate<T> predicate) {
        return predicate.test(t) ? t : null;
    }

    public static <T> T takeUnless(T t, Predicate<T> predicate) {
        return predicate.test(t) ? null : t;
    }

    public static void repeat(int times, Consumer<Integer> runnable) {
        for (int i = 0; i < times; i++) {
            runnable.accept(i);
        }
    }

}

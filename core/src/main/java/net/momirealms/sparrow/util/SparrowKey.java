package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;

public record SparrowKey(@NotNull String namespace, @NotNull String value) {
    public static final String DEFAULT_NAMESPACE = "sparrow";

    @NotNull
    public static SparrowKey sparrow(@NotNull String value) {
        return new SparrowKey(DEFAULT_NAMESPACE, value);
    }

    // 解析 "命名空间:值", 没有写命名空间时使用 sparrow
    @NotNull
    public static SparrowKey of(@NotNull String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? sparrow(id) : new SparrowKey(id.substring(0, separator), id.substring(separator + 1));
    }

    // 命名空间是 sparrow 时只写值
    @NotNull
    public String asMinimalString() {
        return this.namespace.equals(DEFAULT_NAMESPACE) ? this.value : this.toString();
    }

    @NotNull
    @Override
    public String toString() {
        return this.namespace + ":" + this.value;
    }
}

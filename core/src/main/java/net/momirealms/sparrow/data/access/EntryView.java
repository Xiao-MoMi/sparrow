package net.momirealms.sparrow.data.access;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/** 一个 key 下的全部条目. 本地副本的视图会随通知更新, 遍历期间可能看到并发修改. */
public interface EntryView<E, V> {

    @Nullable
    V get(@NotNull E entry);

    @NotNull
    default V getOrDefault(@NotNull E entry, @NotNull V fallback) {
        V value = this.get(entry);
        return value == null ? fallback : value;
    }

    int size();

    void forEach(@NotNull BiConsumer<? super E, ? super V> action);
}
package net.momirealms.sparrow.data.access;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface DataReader<E, V> {

    /** 只看本地副本. 本服没有这个 key 的副本时返回 null. */
    @Nullable
    EntryView<E, V> peek();

    /** 优先使用本地副本, 缺失时读取 Redis, 必要时回源数据库. 没有记录时得到空视图. */
    @NotNull
    CompletableFuture<EntryView<E, V>> read();
}
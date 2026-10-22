package net.momirealms.sparrow.data.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface DataStore<K, E, V> {

    /** 读取这个 key 的全部条目. 没有记录时返回空 Map, 读取失败时让 Future 异常完成. */
    @NotNull
    CompletableFuture<Map<E, V>> load(@NotNull K key);

    /** 保存一个批次, 实现应让写入和删除整体生效. Future 在数据库操作完成后才结束. */
    @NotNull
    CompletableFuture<Void> save(@NotNull K key, @NotNull Map<E, V> upserts, @NotNull Set<E> removals);
}
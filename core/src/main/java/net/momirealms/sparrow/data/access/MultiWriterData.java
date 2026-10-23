package net.momirealms.sparrow.data.access;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MultiWriterData<E, V> {

    /** 取得这个 key 的访问入口, 本身不触发加载. */
    @NotNull
    MultiWriterEntries<E, V> entries(@NotNull UUID key);

    /** 保存 Redis 中本次选中的 key. 多个实例可以同时调用, 不追赶之后新加入的 key. */
    @NotNull
    CompletableFuture<Void> saveDirty();
}
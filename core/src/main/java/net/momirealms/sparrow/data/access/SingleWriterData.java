package net.momirealms.sparrow.data.access;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface SingleWriterData<K, E, V> {

    /** 只读入口, 任何服务器都可使用, 只能看到写入方已发布的内容. */
    @NotNull
    DataReader<E, V> reader(@NotNull K key);

    /** 本服同键复用一个写容器, 直到 release 完成. 跨服唯一写入者由调用方安排. */
    @NotNull
    CompletableFuture<SingleWriterEntries<E, V>> load(@NotNull K key);

    /** 等写容器已接受的发布和保存结束后释放它, 未发布的修改直接丢弃. 释放后旧句柄不能再修改或发布. */
    @NotNull
    CompletableFuture<Void> release(@NotNull K key);

    /** 保存本服写容器中尚未落库的修改, 不包含只读副本, 也不追赶后续修改. */
    @NotNull
    CompletableFuture<Void> saveDirty();
}
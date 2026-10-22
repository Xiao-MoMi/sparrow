package net.momirealms.sparrow.data.access;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface MultiWriterEntries<E, V> extends DataReader<E, V> {

    /** 写入或覆盖一个条目, 等同于只做一次 put 的 modify. */
    @NotNull
    CompletableFuture<Void> put(@NotNull E entry, @NotNull V value);

    /** 返回删除前条目是否存在. */
    @NotNull
    CompletableFuture<Boolean> remove(@NotNull E entry);

    /** 函数收到当前值并返回新值, 返回 null 时删除该条目. 与 modify 一样可能重算. */
    @NotNull
    CompletableFuture<@Nullable V> update(@NotNull E entry, @NotNull UnaryOperator<@Nullable V> operation);

    /**
     * 在本地副本上执行回调, 再按 key 的版本提交暂存的修改, 返回值即业务结果.
     * 版本已变化时用最新状态重跑回调, <strong>回调必须没有外部副作用</strong>, 编辑器只在回调期间有效.
     */
    @NotNull
    <R> CompletableFuture<R> modify(@NotNull Function<EntryEdit<E, V>, R> operation);

    /** 把这个 key 在 Redis 中尚未落库的条目保存到数据库, 调用前已提交的修改都包含在内. */
    @NotNull
    CompletableFuture<Void> save();
}
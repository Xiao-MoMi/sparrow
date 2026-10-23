package net.momirealms.sparrow.data.access;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** 本地写容器. 读写都是本地同步操作, publish/save 才产生 I/O. */
public interface SingleWriterEntries<E, V> extends EntryEdit<E, V> {

    /** 函数收到当前值并返回新值, 返回 null 时删除该条目. */
    @Nullable
    V update(@NotNull E entry, @NotNull UnaryOperator<@Nullable V> operation);

    /** 在写容器锁内执行回调, 回调直接修改本容器, 返回值即业务结果. 回调抛出异常时已做的修改保留. */
    <R> R modify(@NotNull Function<EntryEdit<E, V>, R> operation);

    /** 发布调用前的本地修改, 只发送变化的条目. 完成时 Redis 已能提供这些修改. */
    @NotNull
    CompletableFuture<Void> publish();

    /** 发布本地修改, 再把这个 key 在 Redis 中尚未落库的条目保存到数据库. */
    @NotNull
    CompletableFuture<Void> save();

    /** 等已接受的发布和保存结束后关闭, 未发布的修改直接丢弃, 不执行 I/O. <strong>关闭后不能再修改, 发布或保存</strong>. */
    @NotNull
    CompletableFuture<Void> close();
}
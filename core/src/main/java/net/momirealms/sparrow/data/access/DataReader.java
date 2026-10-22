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

    /**
     * 加载并固定本服副本, 已有副本直接复用, 不强制刷新 Redis.
     * 每次申请返回独立句柄, <strong>使用结束后必须 close</strong>; 取消尚未完成的申请只撤销本次持有.
     */
    @NotNull
    CompletableFuture<DataHold<E, V>> hold();
}
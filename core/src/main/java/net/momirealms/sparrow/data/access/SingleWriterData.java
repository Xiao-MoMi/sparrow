package net.momirealms.sparrow.data.access;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SingleWriterData<E, V> {

    /** 只读入口, 任何服务器都可使用, 只能看到写入方已发布的内容. */
    @NotNull
    DataReader<E, V> reader(@NotNull UUID key);

    /** 每次调用加载一个独立的写容器, <strong>用完后必须 close</strong>. 跨服和本服的唯一写入者都由调用方安排. */
    @NotNull
    CompletableFuture<SingleWriterEntries<E, V>> load(@NotNull UUID key);

    /** 保存 Redis 中本次选中的 key, 只包含已发布的修改. 多个实例可以同时调用, 不追赶之后新加入的 key. */
    @NotNull
    CompletableFuture<Void> saveDirty();
}
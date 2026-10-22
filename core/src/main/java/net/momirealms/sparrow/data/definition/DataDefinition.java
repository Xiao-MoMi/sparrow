package net.momirealms.sparrow.data.definition;

import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;

/** 同一环境内, 相同 ID 必须使用一致的写入模型和编码. 条目键不能编码为空字节, 读到的值<strong>只读</strong>. */
public interface DataDefinition<K, E, V> {

    @NotNull
    SparrowKey id();

    @NotNull
    DataCodec<K> keyCodec();

    @NotNull
    DataCodec<E> entryCodec();

    @NotNull
    DataCodec<V> valueCodec();

    @NotNull
    DataStore<K, E, V> store();

    @NotNull
    CachePolicy cachePolicy();
}
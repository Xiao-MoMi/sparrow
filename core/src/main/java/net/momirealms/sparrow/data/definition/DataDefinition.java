package net.momirealms.sparrow.data.definition;

import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** 数据按玩家 UUID 分 key. 同一环境内, 相同 ID 必须使用一致的写入模型和编码. 条目键不能编码为空字节, 读到的值<strong>只读</strong>. */
public interface DataDefinition<E, V> {

    @NotNull
    SparrowKey id();

    @NotNull
    DataCodec<E> entryCodec();

    @NotNull
    DataCodec<V> valueCodec();

    @NotNull
    DataStore<E, V> store();

    @NotNull
    CachePolicy cachePolicy();

    /** Redis 干净数据的保留时间, null 表示不过期; 同一 ID 在各服须配置一致. */
    @Nullable
    default Duration redisTtl() {
        return null;
    }
}
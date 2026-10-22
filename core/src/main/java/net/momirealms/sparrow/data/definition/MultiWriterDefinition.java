package net.momirealms.sparrow.data.definition;

import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 多个实例共同修改 Redis 中的当前值. 配置方法返回新的定义. */
public record MultiWriterDefinition<K, E, V>(
        @NotNull SparrowKey id,
        @NotNull DataCodec<K> keyCodec,
        @NotNull DataCodec<E> entryCodec,
        @NotNull DataCodec<V> valueCodec,
        @NotNull DataStore<K, E, V> store,
        @NotNull CachePolicy cachePolicy
) implements DataDefinition<K, E, V> {

    /** 使用默认读副本策略创建定义. */
    @NotNull
    public static <K, E, V> MultiWriterDefinition<K, E, V> of(
            @NotNull SparrowKey id,
            @NotNull DataCodec<K> keys,
            @NotNull DataCodec<E> entries,
            @NotNull DataCodec<V> values,
            @NotNull DataStore<K, E, V> store
    ) {
        return new MultiWriterDefinition<>(id, keys, entries, values, store, CachePolicy.DEFAULT);
    }

    /** 返回改用指定读副本策略的新定义. */
    @NotNull
    public MultiWriterDefinition<K, E, V> cache(@NotNull Duration idleTimeout, long maximumSize) {
        return new MultiWriterDefinition<>(this.id, this.keyCodec, this.entryCodec, this.valueCodec, this.store, new CachePolicy(idleTimeout, maximumSize));
    }
}
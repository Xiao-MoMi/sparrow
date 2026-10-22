package net.momirealms.sparrow.data.definition;

import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** 多个实例共同修改 Redis 中的当前值. 配置方法返回新的定义. */
public record MultiWriterDefinition<K, E, V>(
        @NotNull SparrowKey id,
        @NotNull DataCodec<K> keyCodec,
        @NotNull DataCodec<E> entryCodec,
        @NotNull DataCodec<V> valueCodec,
        @NotNull DataStore<K, E, V> store,
        @NotNull CachePolicy cachePolicy,
        @Nullable Duration redisTtl
) implements DataDefinition<K, E, V> {

    public MultiWriterDefinition {
        if (redisTtl != null && redisTtl.toMillis() <= 0) throw new IllegalArgumentException("Redis TTL must be at least one millisecond");
    }

    public MultiWriterDefinition(
            @NotNull SparrowKey id,
            @NotNull DataCodec<K> keyCodec,
            @NotNull DataCodec<E> entryCodec,
            @NotNull DataCodec<V> valueCodec,
            @NotNull DataStore<K, E, V> store,
            @NotNull CachePolicy cachePolicy
    ) {
        this(id, keyCodec, entryCodec, valueCodec, store, cachePolicy, null);
    }

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
        return new MultiWriterDefinition<>(this.id, this.keyCodec, this.entryCodec, this.valueCodec, this.store, new CachePolicy(idleTimeout, maximumSize), this.redisTtl);
    }

    /** 返回改用指定 Redis 保留时间的新定义, null 关闭过期; 有值时须至少为 1 毫秒. */
    @NotNull
    public MultiWriterDefinition<K, E, V> redisTtl(@Nullable Duration duration) {
        return new MultiWriterDefinition<>(this.id, this.keyCodec, this.entryCodec, this.valueCodec, this.store, this.cachePolicy, duration);
    }

}
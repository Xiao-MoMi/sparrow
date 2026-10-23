package net.momirealms.sparrow.data.definition;

import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** 同一键由调用方安排唯一写入者. 配置方法返回新的定义, 不修改已注册的数据. */
public record SingleWriterDefinition<E, V>(
        @NotNull SparrowKey id,
        @NotNull DataCodec<E> entryCodec,
        @NotNull DataCodec<V> valueCodec,
        @NotNull DataStore<E, V> store,
        @NotNull CachePolicy cachePolicy,
        @Nullable Duration redisTtl
) implements DataDefinition<E, V> {

    public SingleWriterDefinition {
        if (redisTtl != null && redisTtl.toMillis() <= 0) throw new IllegalArgumentException("Redis TTL must be at least one millisecond");
    }

    public SingleWriterDefinition(
            @NotNull SparrowKey id,
            @NotNull DataCodec<E> entryCodec,
            @NotNull DataCodec<V> valueCodec,
            @NotNull DataStore<E, V> store,
            @NotNull CachePolicy cachePolicy
    ) {
        this(id, entryCodec, valueCodec, store, cachePolicy, null);
    }

    /** 使用默认读副本策略创建定义. */
    @NotNull
    public static <E, V> SingleWriterDefinition<E, V> of(
            @NotNull SparrowKey id,
            @NotNull DataCodec<E> entries,
            @NotNull DataCodec<V> values,
            @NotNull DataStore<E, V> store
    ) {
        return new SingleWriterDefinition<>(id, entries, values, store, CachePolicy.DEFAULT);
    }

    /** 返回改用指定读副本策略的新定义. 写容器不受这项策略影响. */
    @NotNull
    public SingleWriterDefinition<E, V> cache(@NotNull Duration idleTimeout, long maximumSize) {
        return new SingleWriterDefinition<>(this.id, this.entryCodec, this.valueCodec, this.store, new CachePolicy(idleTimeout, maximumSize), this.redisTtl);
    }

    /** 返回改用指定 Redis 保留时间的新定义, null 关闭过期; 有值时须至少为 1 毫秒. */
    @NotNull
    public SingleWriterDefinition<E, V> redisTtl(@Nullable Duration duration) {
        return new SingleWriterDefinition<>(this.id, this.entryCodec, this.valueCodec, this.store, this.cachePolicy, duration);
    }

}
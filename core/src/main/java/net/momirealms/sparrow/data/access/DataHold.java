package net.momirealms.sparrow.data.access;

/** 固定在本服的只读副本, 持有期间继续随通知更新. 不授予写入权, 也不强制刷新 Redis. */
public interface DataHold<E, V> extends EntryView<E, V>, AutoCloseable {

    /**
     * 解除本次持有, 重复调用没有额外作用, 不执行 I/O.
     * 最后一次解除后副本回到普通缓存并重新计算空闲时间; <strong>关闭后或数据类型注销后不得继续使用此句柄</strong>.
     */
    @Override
    void close();
}
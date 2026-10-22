package net.momirealms.sparrow.data.access;

import org.jetbrains.annotations.NotNull;

/** 可修改的条目视图. 多写 modify 中的修改先暂存, 回调结束后一起提交. */
public interface EntryEdit<E, V> extends EntryView<E, V> {

    void put(@NotNull E entry, @NotNull V value);

    /** 返回删除前条目是否存在. */
    boolean remove(@NotNull E entry);
}
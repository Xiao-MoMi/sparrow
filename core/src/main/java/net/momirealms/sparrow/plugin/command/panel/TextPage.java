package net.momirealms.sparrow.plugin.command.panel;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 一页已查询完成的数据. index 从 0 开始, size 为正数, total 为非负数; 有效页码由调用方确定.
 */
public record TextPage<T>(int index, int size, long total, @NotNull List<T> content) {

    public TextPage {
        content = List.copyOf(content);
    }

    public int count() {
        return this.total == 0 ? 1 : Math.toIntExact((this.total - 1) / this.size + 1);
    }

    public boolean hasPrevious() {
        return this.index > 0;
    }

    public boolean hasNext() {
        return this.index + 1 < this.count();
    }
}
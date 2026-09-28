package net.momirealms.sparrow.plugin.command.panel;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 文本面板的一页数据.
 *
 * @param index 页码, 从 0 开始
 * @param size 每页条数
 * @param total 满足条件的总条数
 * @param content 当前页的内容
 */
public record TextPage<T>(int index, int size, long total, @NotNull List<T> content) {

    public TextPage {
        content = List.copyOf(content);
    }

    /**
     * 先查询总数并把页码夹在有效范围内, 再只读取这一页, 不会一次取回全部数据.
     *
     * @param count 查询总数
     * @param list 按 (offset, limit) 读取一页
     * @param index 期望的页码, 从 0 开始, 越界时夹回首页或末页
     * @param size 每页条数
     * @return 读取任务
     */
    @NotNull
    public static <T> CompletableFuture<TextPage<T>> load(@NotNull Supplier<CompletableFuture<Long>> count,
                                                        @NotNull BiFunction<Integer, Integer, CompletableFuture<List<T>>> list,
                                                        int index, int size) {
        return load(count, list, index, size, true);
    }

    private static <T> CompletableFuture<TextPage<T>> load(Supplier<CompletableFuture<Long>> count, BiFunction<Integer, Integer, CompletableFuture<List<T>>> list,
                                                         int index, int size, boolean retryEmpty) {
        return count.get().thenCompose(total -> {
            if (total == 0) return CompletableFuture.completedFuture(new TextPage<T>(0, size, 0, List.of()));
            int actualIndex = Math.clamp(index, 0, count(total, size) - 1);
            return list.apply(Math.multiplyExact(actualIndex, size), size).thenCompose(content -> {
                // 计数之后发生删除时重查一次, 把已消失的尾页夹回新的末页
                if (content.isEmpty() && retryEmpty) return load(count, list, actualIndex, size, false);
                return CompletableFuture.completedFuture(new TextPage<>(actualIndex, size, total, content));
            });
        });
    }

    public int count() {
        return count(this.total, this.size);
    }

    public boolean hasPrevious() {
        return this.index > 0;
    }

    public boolean hasNext() {
        return this.index < this.count() - 1;
    }

    private static int count(long total, int size) {
        return total == 0 ? 1 : Math.toIntExact((total - 1) / size + 1);
    }
}

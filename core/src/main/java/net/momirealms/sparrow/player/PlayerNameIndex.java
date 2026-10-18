package net.momirealms.sparrow.player;

import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

final class PlayerNameIndex {
    private static final int BUCKETS = 128;
    private static final NameEntry[] EMPTY = new NameEntry[0];
    private static final Comparator<NameEntry> ORDER = Comparator.comparing(NameEntry::key).thenComparing(NameEntry::uniqueId);

    private final AtomicReferenceArray<NameEntry[]> buckets = new AtomicReferenceArray<>(BUCKETS); // 按首字符分槽, 写入方在名单锁内整槽替换

    PlayerNameIndex() {
        this.clear();
    }

    // 启动加载时一次装入全部条目, 整体替换原有内容, 每个槽只排序一次
    void rebuild(@NotNull List<NameEntry> entries) {
        int size = entries.size();
        int[] counts = new int[BUCKETS];
        for (int i = 0; i < size; i++) {
            counts[bucket(entries.get(i).key())]++;
        }
        NameEntry[][] arrays = new NameEntry[BUCKETS][];
        for (int bucket = 0; bucket < BUCKETS; bucket++) {
            arrays[bucket] = counts[bucket] == 0 ? EMPTY : new NameEntry[counts[bucket]];
            counts[bucket] = 0;
        }
        // 先按槽分组, 每组排序后装入
        for (int i = 0; i < size; i++) {
            NameEntry entry = entries.get(i);
            int bucket = bucket(entry.key());
            arrays[bucket][counts[bucket]++] = entry;
        }
        for (int bucket = 0; bucket < BUCKETS; bucket++) {
            Arrays.sort(arrays[bucket], ORDER);
            this.buckets.set(bucket, arrays[bucket]);
        }
    }

    void insert(@NotNull NameEntry entry) {
        int bucket = bucket(entry.key());
        NameEntry[] array = this.buckets.get(bucket);
        int index = -Arrays.binarySearch(array, entry, ORDER) - 1;
        NameEntry[] updated = new NameEntry[array.length + 1];
        System.arraycopy(array, 0, updated, 0, index);
        updated[index] = entry;
        System.arraycopy(array, index, updated, index + 1, array.length - index);
        this.buckets.set(bucket, updated);
    }

    void remove(@NotNull NameEntry entry) {
        int bucket = bucket(entry.key());
        NameEntry[] array = this.buckets.get(bucket);
        int index = Arrays.binarySearch(array, entry, ORDER);
        NameEntry[] updated = new NameEntry[array.length - 1];
        System.arraycopy(array, 0, updated, 0, index);
        System.arraycopy(array, index + 1, updated, index, updated.length - index);
        this.buckets.set(bucket, updated);
    }

    // 删除所有满足条件的条目, 每个受影响的槽只替换一次
    @NotNull
    List<NameEntry> removeIf(@NotNull Predicate<NameEntry> filter) {
        List<NameEntry> removed = new ArrayList<>();
        for (int bucket = 0; bucket < BUCKETS; bucket++) {
            NameEntry[] array = this.buckets.get(bucket);
            int start = removed.size();
            for (NameEntry entry : array) {
                if (filter.test(entry)) {
                    removed.add(entry);
                }
            }
            if (removed.size() == start) continue;
            // 删除的条目与槽内顺序一致, 依次跳过即可
            NameEntry[] updated = new NameEntry[array.length - (removed.size() - start)];
            int index = 0;
            int next = start;
            for (NameEntry entry : array) {
                if (next < removed.size() && removed.get(next) == entry) {
                    next++;
                } else {
                    updated[index++] = entry;
                }
            }
            this.buckets.set(bucket, updated);
        }
        return removed;
    }

    void clear() {
        for (int bucket = 0; bucket < BUCKETS; bucket++) {
            this.buckets.set(bucket, EMPTY);
        }
    }

    // 同名条目可能来自宕机服务器留下的旧记录, 返回第一个解析成功的结果
    @Nullable
    <T> T find(@NotNull String key, @NotNull Function<NameEntry, T> resolver) {
        if (key.isEmpty()) return null;
        NameEntry[] array = this.buckets.get(bucket(key));
        for (int index = lowerBound(array, key); index < array.length; index++) {
            NameEntry entry = array[index];
            if (!entry.key().equals(key)) return null;
            T result = resolver.apply(entry);
            if (result != null) return result;
        }
        return null;
    }

    // 按名字顺序访问以小写前缀开头的条目, 空前缀依次访问全部槽
    void forEach(@NotNull String prefix, @NotNull Consumer<NameEntry> action) {
        if (prefix.isEmpty()) {
            for (int bucket = 0; bucket < BUCKETS; bucket++) {
                for (NameEntry entry : this.buckets.get(bucket)) {
                    action.accept(entry);
                }
            }
            return;
        }
        NameEntry[] array = this.buckets.get(bucket(prefix));
        for (int index = lowerBound(array, prefix); index < array.length; index++) {
            NameEntry entry = array[index];
            if (!entry.key().startsWith(prefix)) return;
            action.accept(entry);
        }
    }

    // 槽号就是首字符的字符值, 按槽号遍历即为全局名字顺序
    private static int bucket(String key) {
        return Math.min(key.charAt(0), BUCKETS - 1);
    }

    private static int lowerBound(NameEntry[] array, String key) {
        int low = 0;
        int high = array.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (array[middle].key().compareTo(key) < 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    // key 是小写名字, 补全候选保留原始大小写
    record NameEntry(@NotNull String key, @NotNull UUID uniqueId, @NotNull Suggestion suggestion) {
    }
}
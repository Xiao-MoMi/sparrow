package net.momirealms.sparrow.data.internal;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable.TableEntry;
import net.momirealms.sparrow.data.access.EntryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiConsumer;

// 一个 key 在本服的条目副本. 读取不加锁, 写入由持有者串行进行
final class LocalEntries<E, V> implements EntryView<E, V> {
    final ConcurrentChainedObject2ObjectHashTable<E, V> entries = new ConcurrentChainedObject2ObjectHashTable<>();
    volatile long version;

    LocalEntries(long version) {
        this.version = version;
    }

    @Nullable
    @Override
    public V get(@NotNull E entry) {
        return this.entries.get(entry);
    }

    @NotNull
    @Override
    public V getOrDefault(@NotNull E entry, @NotNull V fallback) {
        return this.entries.getOrDefault(entry, fallback);
    }

    @Override
    public int size() {
        return this.entries.size();
    }

    @Override
    public void forEach(@NotNull BiConsumer<? super E, ? super V> action) {
        for (TableEntry<E, V> entry : this.entries) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    // 先写条目再推进版本, 先读版本的一方看到的条目不会比这个版本旧. changes 中的 null 表示删除
    void apply(@NotNull Map<E, V> changes, long version) {
        for (Map.Entry<E, V> change : changes.entrySet()) {
            if (change.getValue() == null) {
                this.entries.remove(change.getKey());
            } else {
                this.entries.put(change.getKey(), change.getValue());
            }
        }
        this.version = version;
    }
}
package net.momirealms.sparrow.data.internal;

import net.momirealms.sparrow.data.access.DataReader;
import net.momirealms.sparrow.data.access.DataHold;
import net.momirealms.sparrow.data.access.EntryEdit;
import net.momirealms.sparrow.data.access.EntryView;
import net.momirealms.sparrow.data.access.MultiWriterEntries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

final class MultiWriterEntriesImpl<K, E, V> implements MultiWriterEntries<E, V> {
    private final MultiWriterDataImpl<K, E, V> owner;
    private final K key;
    private final DataReader<E, V> reader;

    MultiWriterEntriesImpl(@NotNull MultiWriterDataImpl<K, E, V> owner, @NotNull K key) {
        this.owner = owner;
        this.key = key;
        this.reader = owner.reader(key);
    }

    @Nullable
    @Override
    public EntryView<E, V> peek() {
        return this.reader.peek();
    }

    @NotNull
    @Override
    public CompletableFuture<EntryView<E, V>> read() {
        return this.reader.read();
    }

    @NotNull
    @Override
    public CompletableFuture<DataHold<E, V>> hold() {
        return this.reader.hold();
    }

    @NotNull
    @Override
    public CompletableFuture<Void> put(@NotNull E entry, @NotNull V value) {
        return this.modify(edit -> {
            edit.put(entry, value);
            return null;
        });
    }

    @NotNull
    @Override
    public CompletableFuture<Boolean> remove(@NotNull E entry) {
        return this.modify(edit -> edit.remove(entry));
    }

    @NotNull
    @Override
    public CompletableFuture<@Nullable V> update(@NotNull E entry, @NotNull UnaryOperator<@Nullable V> operation) {
        return this.modify(edit -> {
            V value = operation.apply(edit.get(entry));
            if (value == null) {
                edit.remove(entry);
            } else {
                edit.put(entry, value);
            }
            return value;
        });
    }

    @NotNull
    @Override
    public <R> CompletableFuture<R> modify(@NotNull Function<EntryEdit<E, V>, R> operation) {
        return this.owner.execute(() -> this.owner.cache.read(this.key).thenCompose(entries -> this.modify(entries, operation)));
    }

    // 回调在工作执行器上对副本计算. 版本不符时脚本带回最新状态, 用它重跑回调
    private <R> CompletableFuture<R> modify(LocalEntries<E, V> base, Function<EntryEdit<E, V>, R> operation) {
        Edit<E, V> edit = new Edit<>(base);
        return CompletableFuture.supplyAsync(() -> edit.run(operation), this.owner.executor)
                .thenCompose(result -> this.owner.backend.commit(this.key, edit.version, edit.changes).thenCompose(commit -> {
                    if (commit.current() != null) return this.modify(commit.current(), operation);
                    if (!edit.changes.isEmpty()) {
                        this.owner.cache.applied(this.key, commit.version(), edit.changes);
                    }
                    return CompletableFuture.completedFuture(result);
                }));
    }

    @NotNull
    @Override
    public CompletableFuture<Void> save() {
        return this.owner.execute(() -> this.owner.backend.save(this.key));
    }

    // 暂存一次回调中的修改, 读取优先看暂存结果
    private static final class Edit<E, V> implements EntryEdit<E, V> {
        private final LocalEntries<E, V> base;
        private final Map<E, V> changes = new HashMap<>(); // null 表示删除
        private long version;
        private int size;

        private Edit(LocalEntries<E, V> base) {
            this.base = base;
        }

        // 先读版本再读条目, 读到比版本新的条目只会让提交因版本不符而重跑
        private <R> R run(Function<EntryEdit<E, V>, R> operation) {
            this.version = this.base.version;
            this.size = this.base.size();
            return operation.apply(this);
        }

        @Nullable
        @Override
        public V get(@NotNull E entry) {
            V changed = this.changes.get(entry);
            if (changed != null || this.changes.containsKey(entry)) return changed;
            return this.base.get(entry);
        }

        @Override
        public int size() {
            return this.size;
        }

        @Override
        public void forEach(@NotNull BiConsumer<? super E, ? super V> action) {
            this.base.forEach((entry, value) -> {
                if (!this.changes.containsKey(entry)) {
                    action.accept(entry, value);
                }
            });
            this.changes.forEach((entry, value) -> {
                if (value != null) {
                    action.accept(entry, value);
                }
            });
        }

        @Override
        public void put(@NotNull E entry, @NotNull V value) {
            if (this.get(entry) == null) {
                this.size++;
            }
            this.changes.put(entry, value);
        }

        @Override
        public boolean remove(@NotNull E entry) {
            if (this.get(entry) == null) return false;
            this.size--;
            this.changes.put(entry, null);
            return true;
        }
    }
}
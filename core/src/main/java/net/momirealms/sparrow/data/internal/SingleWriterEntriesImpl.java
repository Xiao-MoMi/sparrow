package net.momirealms.sparrow.data.internal;

import net.momirealms.sparrow.data.access.EntryEdit;
import net.momirealms.sparrow.data.access.SingleWriterEntries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

final class SingleWriterEntriesImpl<E, V> implements SingleWriterEntries<E, V> {
    private final SingleWriterDataImpl<E, V> owner;
    private final UUID key;
    private final LocalEntries<E, V> entries;
    private final Set<E> dirty = new HashSet<>();                       // 尚未发布的条目
    private final Set<CompletableFuture<Void>> active = new HashSet<>(); // close 需要等待的发布和保存
    private CompletableFuture<Void> publication = CompletableFuture.completedFuture(null);
    private CompletableFuture<Void> saving = CompletableFuture.completedFuture(null);
    private boolean unsaved = true; // 已发布或接手时 Redis 中可能有尚未落库的修改
    private boolean closed;

    SingleWriterEntriesImpl(@NotNull SingleWriterDataImpl<E, V> owner, @NotNull UUID key, @NotNull LocalEntries<E, V> entries) {
        this.owner = owner;
        this.key = key;
        this.entries = entries;
    }

    @NotNull
    UUID key() {
        return this.key;
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
        this.entries.forEach(action);
    }

    @Override
    public synchronized void put(@NotNull E entry, @NotNull V value) {
        if (this.closed) throw new IllegalStateException("Writer is closed");
        this.entries.entries.put(entry, value);
        this.dirty.add(entry);
    }

    @Override
    public synchronized boolean remove(@NotNull E entry) {
        if (this.closed) throw new IllegalStateException("Writer is closed");
        if (this.entries.entries.remove(entry) == null) return false;
        this.dirty.add(entry);
        return true;
    }

    @Nullable
    @Override
    public synchronized V update(@NotNull E entry, @NotNull UnaryOperator<@Nullable V> operation) {
        if (this.closed) throw new IllegalStateException("Writer is closed");
        V value = operation.apply(this.entries.get(entry));
        if (value == null) {
            this.entries.entries.remove(entry);
        } else {
            this.entries.entries.put(entry, value);
        }
        this.dirty.add(entry);
        return value;
    }

    @Override
    public synchronized <R> R modify(@NotNull Function<EntryEdit<E, V>, R> operation) {
        if (this.closed) throw new IllegalStateException("Writer is closed");
        return operation.apply(this);
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> publish() {
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Writer is closed"));
        if (this.dirty.isEmpty()) return this.publication.copy();
        return this.track(this.owner.execute(this.key, this::publishChanges)).copy();
    }

    // 调用方已登记完整操作并持有写容器的锁, 内部发布不再申请一次新操作
    private CompletableFuture<Void> publishChanges() {
        if (this.dirty.isEmpty()) return this.publication;
        Map<E, V> changes = HashMap.newHashMap(this.dirty.size());
        for (E entry : this.dirty) {
            changes.put(entry, this.entries.get(entry));
        }
        this.dirty.clear();
        this.unsaved = true;
        // 锁内立即发出脚本, 同一连接上的发布顺序与修改顺序一致
        this.publication = this.owner.backend.commit(this.key, -1, changes)
                .thenAccept(commit -> this.owner.cache.applied(this.key, commit.version(), changes));
        this.publication.whenComplete((ignored, error) -> {
            if (error != null) {
                synchronized (this) {
                    this.dirty.addAll(changes.keySet());
                }
            }
        });
        return this.publication;
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> save() {
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Writer is closed"));
        if (!this.unsaved && this.dirty.isEmpty()) return this.saving.copy();
        return this.track(this.owner.execute(this.key, this::saveChanges)).copy();
    }

    private CompletableFuture<Void> saveChanges() {
        CompletableFuture<Void> published = this.publishChanges();
        this.unsaved = false;
        this.saving = published.thenCompose(ignored -> this.owner.backend.save(this.key));
        this.saving.whenComplete((ignored, error) -> {
            if (error != null) {
                synchronized (this) {
                    this.unsaved = true;
                }
            }
        });
        return this.saving;
    }

    @NotNull
    @Override
    public synchronized CompletableFuture<Void> close() {
        this.closed = true;
        this.owner.writerClosed(this);
        // 发布和保存的异常已交给各自的调用方, 关闭只等待它们结束
        return CompletableFuture.allOf(this.active.toArray(CompletableFuture[]::new)).handle((ignored, error) -> null);
    }

    private CompletableFuture<Void> track(CompletableFuture<Void> operation) {
        this.active.add(operation);
        operation.whenComplete((ignored, error) -> {
            synchronized (this) {
                this.active.remove(operation);
            }
        });
        return operation;
    }
}
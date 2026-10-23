package net.momirealms.sparrow.data.internal;

import net.momirealms.sparrow.data.access.SingleWriterData;
import net.momirealms.sparrow.data.access.SingleWriterEntries;
import net.momirealms.sparrow.data.definition.SingleWriterDefinition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class SingleWriterDataImpl<E, V> extends RegisteredData<E, V> implements SingleWriterData<E, V> {
    private final Set<SingleWriterEntriesImpl<E, V>> writers = new HashSet<>(); // 尚未关闭的写容器, 它们的 key 需要续期

    public SingleWriterDataImpl(@NotNull SingleWriterDefinition<E, V> definition) {
        super(definition);
    }

    @NotNull
    @Override
    public CompletableFuture<SingleWriterEntries<E, V>> load(@NotNull UUID key) {
        return this.execute(key, () -> this.backend.load(key).thenApply(entries -> {
            SingleWriterEntriesImpl<E, V> writer = new SingleWriterEntriesImpl<>(this, key, entries);
            synchronized (this) {
                this.writers.add(writer);
            }
            return writer;
        }));
    }

    synchronized void writerClosed(@NotNull SingleWriterEntriesImpl<E, V> writer) {
        this.writers.remove(writer);
    }

    @Override
    void collectUsedKeys(@NotNull Set<UUID> keys) {
        super.collectUsedKeys(keys);
        for (SingleWriterEntriesImpl<E, V> writer : this.writers) {
            keys.add(writer.key());
        }
    }
}
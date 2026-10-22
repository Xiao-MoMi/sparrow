package net.momirealms.sparrow.data.internal;

import net.momirealms.sparrow.data.access.MultiWriterData;
import net.momirealms.sparrow.data.access.MultiWriterEntries;
import net.momirealms.sparrow.data.definition.MultiWriterDefinition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public final class MultiWriterDataImpl<K, E, V> extends RegisteredData<K, E, V> implements MultiWriterData<K, E, V> {

    public MultiWriterDataImpl(@NotNull MultiWriterDefinition<K, E, V> definition) {
        super(definition);
    }

    @NotNull
    @Override
    public MultiWriterEntries<E, V> entries(@NotNull K key) {
        return new MultiWriterEntriesImpl<>(this, key);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> saveDirty() {
        return this.execute(() -> this.backend.dirtyKeys().thenCompose(keys -> {
            // 本批只处理这次选中的 key, 之后新增的留给下一次调用
            CompletableFuture<?>[] saving = new CompletableFuture<?>[keys.size()];
            for (int i = 0; i < saving.length; i++) {
                saving[i] = this.backend.save(keys.get(i));
            }
            return CompletableFuture.allOf(saving);
        }));
    }
}
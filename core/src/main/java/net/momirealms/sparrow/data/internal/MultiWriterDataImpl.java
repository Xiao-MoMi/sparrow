package net.momirealms.sparrow.data.internal;

import net.momirealms.sparrow.data.access.MultiWriterData;
import net.momirealms.sparrow.data.access.MultiWriterEntries;
import net.momirealms.sparrow.data.definition.MultiWriterDefinition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@ApiStatus.Internal
public final class MultiWriterDataImpl<E, V> extends RegisteredData<E, V> implements MultiWriterData<E, V> {

    public MultiWriterDataImpl(@NotNull MultiWriterDefinition<E, V> definition) {
        super(definition);
    }

    @NotNull
    @Override
    public MultiWriterEntries<E, V> entries(@NotNull UUID key) {
        return new MultiWriterEntriesImpl<>(this, key);
    }
}
package net.momirealms.sparrow.data.cache;

import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@ApiStatus.Internal
public interface CacheDefinition<K, V> {

    @NotNull
    SparrowKey id();

    @NotNull
    CompletableFuture<@NotNull V> load(@NotNull K key);

    @NotNull
    String encodeKey(@NotNull K key);

    @NotNull
    K decodeKey(@NotNull String key);

    @NotNull
    byte[] encodeValue(@NotNull V value);

    @NotNull
    V decodeValue(byte @NotNull [] value);
}
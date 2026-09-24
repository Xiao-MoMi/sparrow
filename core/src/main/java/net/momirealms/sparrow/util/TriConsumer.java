package net.momirealms.sparrow.util;

@FunctionalInterface
public interface TriConsumer<K, V, S> {
    void accept(K k, V v, S s);
}
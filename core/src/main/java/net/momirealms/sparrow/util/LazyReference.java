package net.momirealms.sparrow.util;

import java.util.function.Supplier;

public interface LazyReference<T> {
    
    T get();

    boolean initialized();

    static <T> LazyReference<T> untilNotNull(final Supplier<T> supplier) {
        return new LazyReference<>() {
            private T value;

            @Override
            public T get() {
                if (this.value == null) {
                    this.value = supplier.get();
                }
                return this.value;
            }

            @Override
            public boolean initialized() {
                return this.value != null;
            }
        };
    }

    static <T> LazyReference<T> oneTime(final Supplier<T> supplier) {
        return new LazyReference<>() {
            private boolean resolved;
            private T value;

            @Override
            public T get() {
                if (!this.resolved) {
                    this.value = supplier.get();
                    this.resolved = true;
                }
                return this.value;
            }

            @Override
            public boolean initialized() {
                return this.resolved;
            }
        };
    }
}

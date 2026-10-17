package net.momirealms.sparrow.util;

import net.momirealms.sparrow.ui.Subscription;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public final class HandlerList<T> {
    private volatile Registration<T>[] registrations = (Registration<T>[]) new Registration<?>[0];

    @NotNull
    public Subscription register(@NotNull T handler) {
        return this.register(handler, EventPriority.NORMAL);
    }

    @NotNull
    public synchronized Subscription register(@NotNull T handler, @NotNull EventPriority priority) {
        Registration<T>[] current = this.registrations;
        int index = 0;
        // 插入同级最后一项之后, 同级回调保持注册顺序.
        while (index < current.length && current[index].priority.compareTo(priority) <= 0) {
            index++;
        }
        Registration<T> registration = new Registration<>(this, handler, priority);
        Registration<T>[] updated = Arrays.copyOf(current, current.length + 1);
        System.arraycopy(current, index, updated, index + 1, current.length - index);
        updated[index] = registration;
        this.registrations = updated;
        return registration;
    }

    public void dispatch(@NotNull Consumer<? super T> invocation) {
        // 本轮固定使用同一份快照, 回调中的注册和注销从后续分发起生效.
        Registration<T>[] current = this.registrations;
        for (Registration<T> registration : current) {
            invocation.accept(registration.handler);
        }
    }

    public synchronized void clear() {
        Registration<T>[] current = this.registrations;
        for (Registration<T> registration : current) {
            registration.closed = true;
        }
        this.registrations = Arrays.copyOf(current, 0);
    }

    private synchronized void unregister(Registration<T> registration) {
        if (registration.closed) return;
        Registration<T>[] current = this.registrations;
        for (int index = 0; index < current.length; index++) {
            if (current[index] != registration) continue;
            Registration<T>[] updated = Arrays.copyOf(current, current.length - 1);
            System.arraycopy(current, index + 1, updated, index, current.length - index - 1);
            registration.closed = true;
            this.registrations = updated;
            return;
        }
    }

    private static final class Registration<T> implements Subscription {
        private final HandlerList<T> owner;
        private final T handler;
        private final EventPriority priority;
        private volatile boolean closed;

        private Registration(HandlerList<T> owner, T handler, EventPriority priority) {
            this.owner = owner;
            this.handler = handler;
            this.priority = priority;
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        @Override
        public void close() {
            this.owner.unregister(this);
        }
    }
}
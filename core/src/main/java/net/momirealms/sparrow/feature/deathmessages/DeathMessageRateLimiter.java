package net.momirealms.sparrow.feature.deathmessages;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class DeathMessageRateLimiter {
    private final ConcurrentChainedObject2ObjectHashTable<UUID, Window> windows = new ConcurrentChainedObject2ObjectHashTable<>();

    boolean record(@NotNull UUID playerId, @NotNull DeathMessagesConfig.AntiSpam settings) {
        if (!settings.enabled()) return true;
        Window window = this.windows.computeIfAbsent(playerId, ignored -> new Window());
        synchronized (window) {
            return window.record(settings, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        }
    }

    void remove(@NotNull UUID playerId) {
        this.windows.remove(playerId);
    }

    void clear() {
        this.windows.clear();
    }

    private static final class Window {
        private final LongArrayFIFOQueue timestamps = new LongArrayFIFOQueue();

        private boolean record(DeathMessagesConfig.AntiSpam settings, long now) {
            while (!this.timestamps.isEmpty() && now - this.timestamps.firstLong() >= settings.intervalMillis()) {
                this.timestamps.dequeueLong();
            }
            this.timestamps.enqueue(now);
            return this.timestamps.size() <= settings.maxMessages();
        }
    }
}
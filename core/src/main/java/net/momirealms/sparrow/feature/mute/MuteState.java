package net.momirealms.sparrow.feature.mute;

import org.jetbrains.annotations.Nullable;

public final class MuteState {
    private volatile @Nullable MuteRecord record;
    private long generation;

    public synchronized long beginLoad() {
        return ++this.generation;
    }

    public synchronized void complete(long generation, @Nullable MuteRecord record) {
        if (this.generation != generation) return;
        this.record = record;
    }

    @Nullable
    public MuteRecord record() {
        return this.record;
    }
}
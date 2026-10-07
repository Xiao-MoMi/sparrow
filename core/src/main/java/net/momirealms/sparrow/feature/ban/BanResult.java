package net.momirealms.sparrow.feature.ban;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record BanResult(@NotNull BanRecord record, @Nullable BanRecord previous, @NotNull Status status) {

    @NotNull
    public static BanResult evaluate(@NotNull BanRecord record, @NotNull List<BanRecord> active, boolean force) {
        if (active.isEmpty()) {
            return new BanResult(record, null, Status.CREATED);
        }
        return new BanResult(record, active.getFirst(), force ? Status.REPLACED : Status.REPLACEMENT_REJECTED);
    }

    public enum Status {
        CREATED,
        REPLACED,
        REPLACEMENT_REJECTED
    }
}

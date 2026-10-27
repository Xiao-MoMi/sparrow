package net.momirealms.sparrow.feature.ban;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BanResult(@NotNull Status status, @Nullable BanRecord record, @Nullable BanRecord previous) {

    public enum Status {
        CREATED, REPLACED, ALREADY_BANNED
    }
}
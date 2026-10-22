package net.momirealms.sparrow.data.definition;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 只控制普通读副本. 时长应为正数, 容量为 0 时不保留读副本. */
public record CachePolicy(@NotNull Duration idleTimeout, long maximumSize) {
    public static final CachePolicy DEFAULT = new CachePolicy(Duration.ofMinutes(5), 10_000);
}
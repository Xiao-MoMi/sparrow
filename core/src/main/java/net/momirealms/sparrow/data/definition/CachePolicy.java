package net.momirealms.sparrow.data.definition;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** 本服有会话的玩家, 读副本一直保留; 其余读副本按这项策略过期和淘汰. 时长应为正数, 容量为 0 时不保留这类副本. */
public record CachePolicy(@NotNull Duration idleTimeout, long maximumSize) {
    public static final CachePolicy DEFAULT = new CachePolicy(Duration.ofMinutes(5), 10_000);
}
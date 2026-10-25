package net.momirealms.sparrow.service.cooldown;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public final class CooldownActiveException extends RuntimeException {
    private final Duration remaining;

    public CooldownActiveException(@NotNull Duration remaining) {
        // 冷却未结束是正常的业务结果, 不需要栈信息
        super("Cooldown active: " + remaining.toMillis() + "ms remaining", null, false, false);
        this.remaining = remaining;
    }

    @NotNull
    public Duration remaining() {
        return this.remaining;
    }
}
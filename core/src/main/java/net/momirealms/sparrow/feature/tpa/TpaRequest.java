package net.momirealms.sparrow.feature.tpa;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record TpaRequest(
        @NotNull UUID requester,
        @NotNull String requesterName,
        @NotNull UUID target,
        @NotNull String targetName,
        @NotNull Direction direction,
        long createdAt
) {

    // 两位玩家的 UUID 是 Redis 哈希的键和字段, 值里只保存其余内容
    @NotNull
    static TpaRequest decode(@NotNull UUID requester, @NotNull UUID target, byte @NotNull [] value) {
        String[] parts = new String(value, StandardCharsets.UTF_8).split(":", 4);
        return new TpaRequest(requester, parts[2], target, parts[3], Direction.valueOf(parts[0]), Long.parseLong(parts[1]));
    }

    byte @NotNull [] encode() {
        return (this.direction + ":" + this.createdAt + ":" + this.requesterName + ":" + this.targetName).getBytes(StandardCharsets.UTF_8);
    }

    public enum Direction {
        TO_TARGET,   // tpa, 发起者去目标身边
        TO_REQUESTER // tpa-here, 目标来发起者身边
    }
}
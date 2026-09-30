package net.momirealms.sparrow.player.teleport;

import org.jetbrains.annotations.NotNull;

public enum TeleportType {
    WARP("warp");

    private final String id; // 冷却在 Redis 中的键名片段

    TeleportType(@NotNull String id) {
        this.id = id;
    }

    @NotNull
    public String id() {
        return this.id;
    }
}

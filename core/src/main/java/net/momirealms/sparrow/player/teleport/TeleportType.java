package net.momirealms.sparrow.player.teleport;

import org.jetbrains.annotations.NotNull;

public enum TeleportType {
    WARP("warp"),
    HOME("home"),
    BACK("back"),
    DEATH_BACK("death-back"),
    BED("bed"),
    SPAWN("spawn");

    private final String id; // 冷却在 Redis 中的键名片段

    TeleportType(@NotNull String id) {
        this.id = id;
    }

    @NotNull
    public String id() {
        return this.id;
    }
}

package net.momirealms.sparrow.player.teleport;

import org.jetbrains.annotations.NotNull;

public enum TeleportResult {
    SUCCESS,        // 已在本服完成传送
    CONNECTING,     // 目标服务器已预留出生位置, 正在切服
    SERVER_OFFLINE, // 目标服务器没有心跳
    INVALID,        // 目标世界不存在
    FAILED,         // 传送被取消或玩家已离线
    COOLDOWN,       // 还在冷却中, 已提示玩家剩余时间
    CANCELLED;      // 预热期间移动、受伤或离开服务器, 已提示玩家原因

    @NotNull
    static TeleportResult of(@NotNull TransferResult result) {
        return switch (result) {
            case SUCCESS -> SUCCESS;
            case CONNECTING -> CONNECTING;
            case SERVER_OFFLINE -> SERVER_OFFLINE;
            case INVALID -> INVALID;
            case FAILED -> FAILED;
        };
    }
}

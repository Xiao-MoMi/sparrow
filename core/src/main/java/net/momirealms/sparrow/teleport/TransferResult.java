package net.momirealms.sparrow.teleport;

public enum TransferResult {
    SUCCESS,        // 已在本服完成传送
    CONNECTING,     // 目标服务器已预留出生位置, 正在切服
    SERVER_OFFLINE, // 目标服务器没有心跳
    INVALID,        // 目标世界不存在
    FAILED          // 传送被取消或玩家已离线
}

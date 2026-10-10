package net.momirealms.sparrow.teleport;

public enum TeleportResult {
    LOCAL_SUCCESS,  // 已在本服完成传送
    REMOTE_SUCCESS, // 代理已确认连接到目标服务器
    SERVER_OFFLINE, // 目标服务器没有心跳
    INVALID,        // 目标世界不存在
    FAILED,         // 本服传送或代理切服失败
    COOLDOWN,       // 还在冷却中, 已提示玩家剩余时间
    CANCELLED       // 预热期间移动、受伤或离开服务器, 已提示玩家原因
}
package net.momirealms.sparrow.teleport;

public enum TeleportResult {
    LOCAL_SUCCESS,  // 已在本服完成传送
    REMOTE_SUCCESS, // 代理已确认连接到目标服务器
    SERVER_OFFLINE, // 目标服务器没有心跳
    INVALID,        // 目标世界不存在
    FAILED,         // 本服传送或代理切服失败
    REJECTED        // 被处理器拒绝, 原因已提示给被传送的玩家
}
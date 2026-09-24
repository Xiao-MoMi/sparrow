package net.momirealms.sparrow.util;

import net.momirealms.sparrow.proxy.minecraft.server.MinecraftServerProxy;

public final class ServerUtils {
    private ServerUtils() {}

    /**
     * 读取原版服务器的运行标志, Paper 和 Spigot 共用同一判断.
     *
     * @return 尚未请求停止服务器时为 true
     */
    public static boolean isRunning() {
        return MinecraftServerProxy.INSTANCE.isRunning(MinecraftServerProxy.INSTANCE.getServer());
    }
}

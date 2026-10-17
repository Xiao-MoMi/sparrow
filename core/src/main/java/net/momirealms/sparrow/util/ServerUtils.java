package net.momirealms.sparrow.util;

import net.momirealms.sparrow.proxy.minecraft.server.MinecraftServerProxy;

public final class ServerUtils {

    private ServerUtils() {}

    public static boolean isRunning() {
        return MinecraftServerProxy.INSTANCE.isRunning(MinecraftServerProxy.INSTANCE.getServer());
    }
}
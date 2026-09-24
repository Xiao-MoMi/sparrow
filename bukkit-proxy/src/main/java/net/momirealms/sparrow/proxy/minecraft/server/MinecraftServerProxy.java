package net.momirealms.sparrow.proxy.minecraft.server;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.server.MinecraftServer")
public interface MinecraftServerProxy {
    MinecraftServerProxy INSTANCE = ASMProxyFactory.create(MinecraftServerProxy.class);

    @MethodInvoker(name = "getServer", isStatic = true)
    Object getServer();

    @MethodInvoker(name = "isRunning")
    boolean isRunning(Object target);
}

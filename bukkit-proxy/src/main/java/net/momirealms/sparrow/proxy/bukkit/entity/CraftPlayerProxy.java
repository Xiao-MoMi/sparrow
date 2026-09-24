package net.momirealms.sparrow.proxy.bukkit.entity;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "org.bukkit.craftbukkit.entity.CraftPlayer")
public interface CraftPlayerProxy {
    CraftPlayerProxy INSTANCE = ASMProxyFactory.create(CraftPlayerProxy.class);

    @MethodInvoker(name = "getHandle")
    Object getHandle(Object target);
}

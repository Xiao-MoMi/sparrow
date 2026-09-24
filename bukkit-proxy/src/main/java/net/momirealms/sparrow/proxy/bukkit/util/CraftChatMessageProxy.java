package net.momirealms.sparrow.proxy.bukkit.util;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "org.bukkit.craftbukkit.util.CraftChatMessage")
public interface CraftChatMessageProxy {
    CraftChatMessageProxy INSTANCE = ASMProxyFactory.create(CraftChatMessageProxy.class);

    @MethodInvoker(name = "fromJSON", isStatic = true)
    Object fromJSON(String json);
}

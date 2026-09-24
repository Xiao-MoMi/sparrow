package net.momirealms.sparrow.proxy.minecraft.network.protocol;

import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.network.protocol.Packet")
public interface PacketProxy {
    PacketProxy INSTANCE = ASMProxyFactory.create(PacketProxy.class);
    Class<?> CLASS = SparrowClass.find("net.minecraft.network.protocol.Packet");

    @MethodInvoker(name = "isTerminal")
    boolean isTerminal(Object target);
}

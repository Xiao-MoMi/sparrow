package net.momirealms.sparrow.proxy.minecraft.network;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.network.Connection")
public interface ConnectionProxy {
    ConnectionProxy INSTANCE = ASMProxyFactory.create(ConnectionProxy.class);

    @FieldGetter(name = "channel")
    Object getChannel(Object target);
}

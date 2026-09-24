package net.momirealms.sparrow.proxy.minecraft.server.network;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.server.network.ServerLoginPacketListenerImpl", activeIf = "!has_patch=paper")
public interface ServerLoginPacketListenerImplProxy {
    ServerLoginPacketListenerImplProxy INSTANCE = ASMProxyFactory.create(ServerLoginPacketListenerImplProxy.class);

    @FieldGetter(name = "connection")
    Object getConnection(Object target);
}

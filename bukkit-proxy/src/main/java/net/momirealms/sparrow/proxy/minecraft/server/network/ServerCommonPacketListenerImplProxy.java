package net.momirealms.sparrow.proxy.minecraft.server.network;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldSetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.server.network.ServerCommonPacketListenerImpl")
public interface ServerCommonPacketListenerImplProxy {
    ServerCommonPacketListenerImplProxy INSTANCE = ASMProxyFactory.create(ServerCommonPacketListenerImplProxy.class);

    @FieldGetter(name = "connection")
    Object getConnection(Object target);

    @FieldSetter(name = "closed")
    void setClosed(Object target, boolean closed);

    @FieldSetter(name = "closedListenerTime")
    void setClosedListenerTime(Object target, long time);
}

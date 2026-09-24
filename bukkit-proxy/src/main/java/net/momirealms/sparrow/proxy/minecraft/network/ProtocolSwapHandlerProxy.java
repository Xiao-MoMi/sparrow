package net.momirealms.sparrow.proxy.minecraft.network;

import net.momirealms.sparrow.proxy.minecraft.network.protocol.PacketProxy;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "net.minecraft.network.ProtocolSwapHandler")
public interface ProtocolSwapHandlerProxy {
    ProtocolSwapHandlerProxy INSTANCE = ASMProxyFactory.create(ProtocolSwapHandlerProxy.class);

    @MethodInvoker(name = "handleInboundTerminalPacket", isStatic = true)
    void handleInboundTerminalPacket(@Type(name = "io.netty.channel.ChannelHandlerContext") Object context, @Type(clazz = PacketProxy.class) Object packet);
}

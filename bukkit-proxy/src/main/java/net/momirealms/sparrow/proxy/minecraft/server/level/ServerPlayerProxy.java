package net.momirealms.sparrow.proxy.minecraft.server.level;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "net.minecraft.server.level.ServerPlayer")
public interface ServerPlayerProxy {
    ServerPlayerProxy INSTANCE = ASMProxyFactory.create(ServerPlayerProxy.class);

    @FieldGetter(name = "connection")
    Object getConnection(Object target);

    @FieldGetter(name = "transferCookieConnection", activeIf = "!has_patch=paper")
    Object getTransferCookieConnection(Object target);

    @MethodInvoker(name = "sendSystemMessage")
    void sendSystemMessage(Object target, @Type(name = "net.minecraft.network.chat.Component") Object message, boolean overlay);
}

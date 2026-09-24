package net.momirealms.sparrow.proxy.paper.connection;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "io.papermc.paper.connection.ReadablePlayerCookieConnectionImpl", activeIf = "has_patch=paper")
public interface ReadablePlayerCookieConnectionProxy {
    ReadablePlayerCookieConnectionProxy INSTANCE = ASMProxyFactory.create(ReadablePlayerCookieConnectionProxy.class);

    @FieldGetter(name = "connection")
    Object getConnection(Object target);
}

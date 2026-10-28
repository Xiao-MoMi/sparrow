package net.momirealms.sparrow.proxy.bukkit.event.player;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

@ReflectionProxy(clazz = AsyncPlayerPreLoginEvent.class, activeIf = "has_patch=paper")
public interface AsyncPlayerPreLoginEventProxy {
    AsyncPlayerPreLoginEventProxy INSTANCE = ASMProxyFactory.create(AsyncPlayerPreLoginEventProxy.class);

    @MethodInvoker(name = "disallow")
    void disallow(
            AsyncPlayerPreLoginEvent event,
            AsyncPlayerPreLoginEvent.Result result,
            @Type(name = "net{}kyori{}adventure{}text{}Component", ignoreRelocation = true) Object message
    );
}
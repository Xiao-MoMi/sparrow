package net.momirealms.sparrow.proxy.minecraft.world.scores;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.world.scores.TeamColor", activeIf = "min_version=26.2")
public interface TeamColorProxy {
    TeamColorProxy INSTANCE = ASMProxyFactory.create(TeamColorProxy.class);

    @MethodInvoker(name = "byName", isStatic = true)
    Object byName(String name);
}

package net.momirealms.sparrow.proxy.minecraft.world.scores;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

import java.util.Optional;

@ReflectionProxy(name = "net.minecraft.world.scores.PlayerTeam")
public interface PlayerTeamProxy {
    PlayerTeamProxy INSTANCE = ASMProxyFactory.create(PlayerTeamProxy.class);

    @MethodInvoker(name = "setColor", activeIf = "min_version=26.2")
    void setColor(Object team, Optional<?> color);
}

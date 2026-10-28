package net.momirealms.sparrow.proxy.bukkit.event.entity;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;
import org.bukkit.event.entity.PlayerDeathEvent;

@ReflectionProxy(clazz = PlayerDeathEvent.class, activeIf = "has_patch=paper")
public interface PlayerDeathEventProxy {
    PlayerDeathEventProxy INSTANCE = ASMProxyFactory.create(PlayerDeathEventProxy.class);

    @MethodInvoker(name = "deathMessage")
    Object deathMessage(PlayerDeathEvent event);

    @MethodInvoker(name = "deathMessage")
    void deathMessage(PlayerDeathEvent event, @Type(name = "net{}kyori{}adventure{}text{}Component", ignoreRelocation = true) Object message);

    @MethodInvoker(name = "deathScreenMessageOverride")
    Object deathScreenMessageOverride(PlayerDeathEvent event);

    @MethodInvoker(name = "deathScreenMessageOverride")
    void deathScreenMessageOverride(PlayerDeathEvent event, @Type(name = "net{}kyori{}adventure{}text{}Component", ignoreRelocation = true) Object message);
}
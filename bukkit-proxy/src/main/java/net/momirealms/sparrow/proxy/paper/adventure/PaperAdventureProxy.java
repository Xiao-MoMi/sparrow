package net.momirealms.sparrow.proxy.paper.adventure;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "io.papermc.paper.adventure.PaperAdventure", activeIf = "has_patch=paper")
public interface PaperAdventureProxy {
    PaperAdventureProxy INSTANCE = ASMProxyFactory.create(PaperAdventureProxy.class);

    @MethodInvoker(name = "asAdventure", isStatic = true)
    Object asAdventure(@Type(name = "net.minecraft.network.chat.Component") Object component);

    @MethodInvoker(name = "asVanilla", isStatic = true)
    Object asVanilla(@Type(name = "net{}kyori{}adventure{}text{}Component", ignoreRelocation = true) Object component);
}
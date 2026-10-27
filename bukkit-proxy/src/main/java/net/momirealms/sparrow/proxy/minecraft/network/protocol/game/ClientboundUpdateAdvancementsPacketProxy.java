package net.momirealms.sparrow.proxy.minecraft.network.protocol.game;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ReflectionProxy(name = "net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket", activeIf = "min_version=26.3")
public interface ClientboundUpdateAdvancementsPacketProxy {
    ClientboundUpdateAdvancementsPacketProxy INSTANCE = ASMProxyFactory.create(ClientboundUpdateAdvancementsPacketProxy.class);

    @ConstructorInvoker
    Object create(boolean shouldReset, List<?> added, Set<?> removed, Map<?, ?> progress, boolean showAdvancements);

    @ReflectionProxy(name = "net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket$PositionedAdvancement", activeIf = "min_version=26.3")
    interface PositionedAdvancementProxy {
        PositionedAdvancementProxy INSTANCE = ASMProxyFactory.create(PositionedAdvancementProxy.class);

        @ConstructorInvoker
        Object create(@Type(name = "net.minecraft.advancements.AdvancementHolder") Object advancement, float x, float y);
    }
}
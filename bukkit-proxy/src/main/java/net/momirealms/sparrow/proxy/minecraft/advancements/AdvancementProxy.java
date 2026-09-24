package net.momirealms.sparrow.proxy.minecraft.advancements;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

import java.util.Map;
import java.util.Optional;

@ReflectionProxy(name = "net.minecraft.advancements.Advancement")
public interface AdvancementProxy {
    AdvancementProxy INSTANCE = ASMProxyFactory.create(AdvancementProxy.class);

    @ConstructorInvoker
    Object create(
            Optional<?> parent,
            Optional<?> display,
            @Type(name = "net.minecraft.advancements.AdvancementRewards") Object rewards,
            Map<String, Object> criteria,
            @Type(name = "net.minecraft.advancements.AdvancementRequirements") Object requirements,
            boolean sendsTelemetryEvent
    );
}

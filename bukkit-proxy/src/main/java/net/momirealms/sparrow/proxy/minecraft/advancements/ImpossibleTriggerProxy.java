package net.momirealms.sparrow.proxy.minecraft.advancements;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = {"net.minecraft.advancements.triggers.ImpossibleTrigger", "net.minecraft.advancements.criterion.ImpossibleTrigger"})
public interface ImpossibleTriggerProxy {
    ImpossibleTriggerProxy INSTANCE = ASMProxyFactory.create(ImpossibleTriggerProxy.class);

    @ConstructorInvoker
    Object create();

    @ReflectionProxy(name = {"net.minecraft.advancements.triggers.ImpossibleTrigger$TriggerInstance", "net.minecraft.advancements.criterion.ImpossibleTrigger$TriggerInstance"})
    interface TriggerInstanceProxy {
        TriggerInstanceProxy INSTANCE = ASMProxyFactory.create(TriggerInstanceProxy.class);

        @ConstructorInvoker
        Object create();
    }
}

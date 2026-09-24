package net.momirealms.sparrow.proxy.minecraft.advancements;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = {"net.minecraft.advancements.triggers.Criterion", "net.minecraft.advancements.Criterion"})
public interface CriterionProxy {
    CriterionProxy INSTANCE = ASMProxyFactory.create(CriterionProxy.class);

    @ConstructorInvoker
    Object create(@Type(clazz = CriterionTriggerProxy.class) Object trigger, @Type(name = "net.minecraft.advancements.CriterionTriggerInstance") Object triggerInstance);
}

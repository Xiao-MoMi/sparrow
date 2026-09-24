package net.momirealms.sparrow.proxy.minecraft.world.item;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "net.minecraft.world.item.ItemStackTemplate", activeIf = "min_version=26.1")
public interface ItemStackTemplateProxy {
    ItemStackTemplateProxy INSTANCE = ASMProxyFactory.create(ItemStackTemplateProxy.class);

    @MethodInvoker(name = "fromNonEmptyStack", isStatic = true)
    Object fromNonEmptyStack(@Type(name = "net.minecraft.world.item.ItemStack") Object stack);
}

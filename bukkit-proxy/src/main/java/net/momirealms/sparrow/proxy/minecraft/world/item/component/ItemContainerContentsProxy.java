package net.momirealms.sparrow.proxy.minecraft.world.item.component;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

import java.util.List;

@ReflectionProxy(name = "net.minecraft.world.item.component.ItemContainerContents")
public interface ItemContainerContentsProxy {
    ItemContainerContentsProxy INSTANCE = ASMProxyFactory.create(ItemContainerContentsProxy.class);

    @ConstructorInvoker(activeIf = "max_version=1.21.11")
    Object create(@Type(name = "net.minecraft.core.NonNullList") Object items);

    @ConstructorInvoker(activeIf = "min_version=26.1")
    Object create$0(List<?> items);

    @FieldGetter(name = "items")
    List<?> getItems(Object target);
}

package net.momirealms.sparrow.proxy.minecraft.world.entity;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = {"net.minecraft.world.entity.EntityTypes", "net.minecraft.world.entity.EntityType"})
public interface EntityTypesProxy {
    EntityTypesProxy INSTANCE = ASMProxyFactory.create(EntityTypesProxy.class);

    @FieldGetter(name = "SLIME", isStatic = true)
    Object getSlime();
}

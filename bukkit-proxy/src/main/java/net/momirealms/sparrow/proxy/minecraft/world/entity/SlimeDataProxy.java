package net.momirealms.sparrow.proxy.minecraft.world.entity;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = {"net.minecraft.world.entity.monster.cubemob.AbstractCubeMob", "net.minecraft.world.entity.monster.Slime"})
public interface SlimeDataProxy {
    SlimeDataProxy INSTANCE = ASMProxyFactory.create(SlimeDataProxy.class);

    @FieldGetter(name = "ID_SIZE", isStatic = true)
    Object getIdSize();
}

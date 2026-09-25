package net.momirealms.sparrow.proxy.minecraft.world.entity;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.world.entity.Entity")
public interface EntityProxy {
    EntityProxy INSTANCE = ASMProxyFactory.create(EntityProxy.class);

    @FieldGetter(name = "DATA_SHARED_FLAGS_ID", isStatic = true)
    Object getDataSharedFlagsId();
}

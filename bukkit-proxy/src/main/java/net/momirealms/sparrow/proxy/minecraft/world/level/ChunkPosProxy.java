package net.momirealms.sparrow.proxy.minecraft.world.level;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.world.level.ChunkPos")
public interface ChunkPosProxy {
    ChunkPosProxy INSTANCE = ASMProxyFactory.create(ChunkPosProxy.class);

    @MethodInvoker(name = {"asLong", "pack"}, isStatic = true)
    long asLong(int x, int z);
}
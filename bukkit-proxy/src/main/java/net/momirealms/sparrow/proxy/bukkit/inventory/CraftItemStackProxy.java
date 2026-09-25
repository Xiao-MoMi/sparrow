package net.momirealms.sparrow.proxy.bukkit.inventory;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;
import org.bukkit.inventory.ItemStack;

@ReflectionProxy(name = "org.bukkit.craftbukkit.inventory.CraftItemStack")
public interface CraftItemStackProxy {
    CraftItemStackProxy INSTANCE = ASMProxyFactory.create(CraftItemStackProxy.class);

    @MethodInvoker(name = {"asCraftMirror", "asBukkitMirror"}, isStatic = true)
    ItemStack asBukkitMirror(@Type(name = "net.minecraft.world.item.ItemStack") Object item);
}

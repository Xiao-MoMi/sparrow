package net.momirealms.sparrow.proxy.bukkit.inventory;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;

@ReflectionProxy(clazz = MenuType.class, activeIf = "has_patch=paper")
public interface MenuTypeProxy {
    MenuTypeProxy INSTANCE = ASMProxyFactory.create(MenuTypeProxy.class);

    @MethodInvoker(name = "create")
    InventoryView create(MenuType type, HumanEntity player, @Type(name = "net{}kyori{}adventure{}text{}Component", ignoreRelocation = true) Object title);
}
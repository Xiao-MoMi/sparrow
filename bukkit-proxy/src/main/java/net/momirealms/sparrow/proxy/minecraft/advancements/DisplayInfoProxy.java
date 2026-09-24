package net.momirealms.sparrow.proxy.minecraft.advancements;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.ConstructorInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

import java.util.Optional;

@ReflectionProxy(name = "net.minecraft.advancements.DisplayInfo")
public interface DisplayInfoProxy {
    DisplayInfoProxy INSTANCE = ASMProxyFactory.create(DisplayInfoProxy.class);

    @ConstructorInvoker(activeIf = "min_version=1.20.3 && max_version=1.21.11")
    Object create(
            @Type(name = "net.minecraft.world.item.ItemStack") Object icon,
            @Type(name = "net.minecraft.network.chat.Component") Object title,
            @Type(name = "net.minecraft.network.chat.Component") Object description,
            Optional<?> background,
            @Type(name = "net.minecraft.advancements.AdvancementType") Object type,
            boolean showToast,
            boolean announceChat,
            boolean hidden
    );

    @ConstructorInvoker(activeIf = "min_version=26.1")
    Object create$0(
            @Type(name = "net.minecraft.world.item.ItemStackTemplate") Object icon,
            @Type(name = "net.minecraft.network.chat.Component") Object title,
            @Type(name = "net.minecraft.network.chat.Component") Object description,
            Optional<?> background,
            @Type(name = "net.minecraft.advancements.AdvancementType") Object type,
            boolean showToast,
            boolean announceChat,
            boolean hidden
    );
}

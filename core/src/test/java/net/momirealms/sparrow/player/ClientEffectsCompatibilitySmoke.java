package net.momirealms.sparrow.player;

import com.mojang.datafixers.util.Pair;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;
import net.momirealms.sparrow.proxy.MinecraftPredicate;
import net.momirealms.sparrow.proxy.minecraft.advancements.AdvancementProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.CriterionProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.DisplayInfoProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.ImpossibleTriggerProxy;
import net.momirealms.sparrow.proxy.minecraft.world.item.ItemStackTemplateProxy;
import net.momirealms.sparrow.reflection.SReflection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 在独立 JVM 中加载指定版本的真实服务端类, 验证消息数据包和进度代理.
 * 第一个参数为版本号; 运行时类路径必须只包含该版本的 NMS, 并使用 -ea 开启断言.
 */
public final class ClientEffectsCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        String version = args[0];
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        SReflection.setActivePredicate(new MinecraftPredicate(version, List.of()));
        boolean templateIcon = version.startsWith("26.");
        if (templateIcon) {
            // 独立测试没有世界数据加载阶段, 为两个测试物品绑定最小组件集.
            var bind = Holder.Reference.class.getMethod("bindComponents", DataComponentMap.class);
            bind.invoke(Items.DIAMOND.builtInRegistryHolder(), DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
            bind.invoke(Items.TOTEM_OF_UNDYING.builtInRegistryHolder(), DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build());
        }
        ItemStack icon = new ItemStack(Items.DIAMOND);
        Component title = Component.literal("Sparrow compatibility");
        icon.set(DataComponents.CUSTOM_NAME, title);
        DisplayInfo display;
        if (templateIcon) {
            Object template = ItemStackTemplateProxy.INSTANCE.fromNonEmptyStack(icon);
            ItemStack recreated = (ItemStack) template.getClass().getMethod("create").invoke(template);
            assert title.equals(recreated.get(DataComponents.CUSTOM_NAME));
            display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create$0(template, title, Component.empty(), Optional.empty(), AdvancementType.CHALLENGE, true, false, true);
        } else {
            display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create(icon, title, Component.empty(), Optional.empty(), AdvancementType.CHALLENGE, true, false, true);
        }
        assert display.shouldShowToast();
        assert !display.shouldAnnounceChat();
        assert display.isHidden();
        Object criterion = CriterionProxy.INSTANCE.create(ImpossibleTriggerProxy.INSTANCE.create(), ImpossibleTriggerProxy.TriggerInstanceProxy.INSTANCE.create());
        AdvancementRequirements requirements = new AdvancementRequirements(List.of(List.of("impossible")));
        Advancement advancement = (Advancement) AdvancementProxy.INSTANCE.create(Optional.empty(), Optional.of(display), AdvancementRewards.EMPTY, Map.of("impossible", criterion), requirements, false);
        assert advancement.criteria().get("impossible") == criterion;
        AdvancementProgress progress = new AdvancementProgress();
        progress.update(requirements);
        progress.grantProgress("impossible");
        assert progress.isDone();
        Identifier id = Identifier.fromNamespaceAndPath("sparrow", "toast");
        ClientboundUpdateAdvancementsPacket grant = new ClientboundUpdateAdvancementsPacket(false, List.of(new AdvancementHolder(id, advancement)), Set.of(), Map.of(id, progress), true);
        ClientboundUpdateAdvancementsPacket remove = new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(id), Map.of(), true);
        new ClientboundBundlePacket(List.of(grant, remove));
        assert grant.getAdded().size() == 1;
        assert remove.getRemoved().contains(id);
        new ClientboundBundlePacket(List.of(new ClientboundSetTitleTextPacket(title), new ClientboundSetSubtitleTextPacket(Component.empty()), new ClientboundSetTitlesAnimationPacket(5, 60, 15)));
        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        totem.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);
        assert totem.has(DataComponents.DEATH_PROTECTION);
        new ClientboundSetEquipmentPacket(1, List.of(Pair.of(EquipmentSlot.MAINHAND, ItemStack.EMPTY), Pair.of(EquipmentSlot.OFFHAND, totem)));
        ClientboundEntityEventPacket.class.getConstructor(Entity.class, byte.class);
        new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, 0.0f);
        new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1.0f);
        System.out.println("PASS " + version + ": toast proxies, icon components, advancement progress, title, equipment, entity-event signature, demo and credits");
    }
}

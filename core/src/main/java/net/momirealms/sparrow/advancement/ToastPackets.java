package net.momirealms.sparrow.advancement;

import net.kyori.adventure.text.Component;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.momirealms.sparrow.proxy.minecraft.advancements.AdvancementProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.CriterionProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.DisplayInfoProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.ImpossibleTriggerProxy;
import net.momirealms.sparrow.proxy.minecraft.world.item.ItemStackTemplateProxy;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 进度提示借用原版进度实现: 授予一个立即完成的隐藏进度, 客户端弹出提示后再移除它.
public final class ToastPackets {
    private static final Identifier TOAST_ID = Identifier.fromNamespaceAndPath("sparrow", "toast");

    private ToastPackets() {
    }

    /**
     * 构造一次进度提示的数据包, 不会在客户端留下进度记录.
     *
     * @param text 提示标题
     * @param icon <strong>非空物品</strong>作为提示图标
     * @param frame 提示的边框样式
     * @return 可直接发送给客户端的捆绑包
     */
    @NotNull
    public static ClientboundBundlePacket create(@NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementFrame frame) {
        net.minecraft.world.item.ItemStack minecraftIcon = CraftItemStack.asNMSCopy(icon);
        net.minecraft.network.chat.Component title = CraftChatMessage.fromJSON(AdventureHelper.componentToJson(text));
        AdvancementType type = AdvancementType.valueOf(frame.name());
        // 26.1 起进度图标改用物品模板
        DisplayInfo display;
        if (VersionHelper.isOrAbove26_1) {
            display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create$0(ItemStackTemplateProxy.INSTANCE.fromNonEmptyStack(minecraftIcon), title, net.minecraft.network.chat.Component.empty(), Optional.empty(), type, true, false, true);
        } else {
            display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create(minecraftIcon, title, net.minecraft.network.chat.Component.empty(), Optional.empty(), type, true, false, true);
        }
        Object criterion = CriterionProxy.INSTANCE.create(ImpossibleTriggerProxy.INSTANCE.create(), ImpossibleTriggerProxy.TriggerInstanceProxy.INSTANCE.create());
        AdvancementRequirements requirements = new AdvancementRequirements(List.of(List.of("impossible")));
        Advancement advancement = (Advancement) AdvancementProxy.INSTANCE.create(Optional.empty(), Optional.of(display), AdvancementRewards.EMPTY, Map.of("impossible", criterion), requirements, false);
        AdvancementProgress progress = new AdvancementProgress();
        progress.update(requirements);
        progress.grantProgress("impossible");
        // 客户端处理完成进度时会保存 Toast, 后续移除临时进度不会移除已排队的提示.
        return new ClientboundBundlePacket(List.of(
                new ClientboundUpdateAdvancementsPacket(false, List.of(new AdvancementHolder(TOAST_ID, advancement)), Set.of(), Map.of(TOAST_ID, progress), true),
                new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(TOAST_ID), Map.of(), true)
        ));
    }
}

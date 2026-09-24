package net.momirealms.sparrow.feature.quickshulker;

import net.kyori.adventure.text.Component;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.momirealms.sparrow.feature.FeatureState;
import net.momirealms.sparrow.proxy.minecraft.world.item.ItemStackTemplateProxy;
import net.momirealms.sparrow.proxy.minecraft.world.item.component.ItemContainerContentsProxy;
import net.momirealms.sparrow.ui.inventory.VirtualInventory;
import net.momirealms.sparrow.ui.inventory.event.SlotChange;
import net.momirealms.sparrow.ui.pane.NormalPane;
import net.momirealms.sparrow.ui.pane.Pane;
import net.momirealms.sparrow.ui.state.Signal;
import net.momirealms.sparrow.ui.util.ItemUtils;
import net.momirealms.sparrow.ui.window.NormalWindow;
import net.momirealms.sparrow.ui.window.Window;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.Components;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class QuickShulkerMenu {
    private final Player viewer;
    private final Inventory playerInventory;
    private final int sourceSlot;
    private final ItemStack shulker;
    private final VirtualInventory contents;
    private final NormalWindow window;
    private boolean invalidated;

    @NotNull
    public static CompletableFuture<Window.OpenResult> open(@NotNull Player viewer, int sourceSlot, @NotNull String title, @NotNull Signal<FeatureState> state) {
        return new QuickShulkerMenu(viewer, sourceSlot, title, state).window.open();
    }

    private QuickShulkerMenu(@NotNull Player viewer, int sourceSlot, @NotNull String title, @NotNull Signal<FeatureState> state) {
        this.viewer = viewer;
        this.playerInventory = ((CraftPlayer) viewer).getHandle().getInventory();
        this.sourceSlot = sourceSlot;
        this.shulker = this.playerInventory.getItem(sourceSlot);
        this.contents = new VirtualInventory(readContents(this.shulker));
        this.contents.setAccessRule(context -> context.player() == this.viewer);

        // 构建窗口
        NormalPane pane = Pane.builder("SSSSSSSSS", "SSSSSSSSS", "SSSSSSSSS")
                .addIngredient('S', this.contents)
                .build();
        Component itemName = AdventureHelper.jsonToComponent(CraftChatMessage.toJSON(this.shulker.getHoverName()));
        this.window = NormalWindow.builder()
                .setUpperPane(pane)
                .setTitle(Components.miniMessage(title, Map.of("hover_name", itemName)))
                .build(viewer);

        // 冻结原潜影盒所在的快捷栏槽位或副手.
        if (sourceSlot == Inventory.SLOT_OFFHAND) this.window.offhandFrozen(true);
        else this.window.frozenAt(this.window.windowSlotAtHotbar(sourceSlot), true);

        // 功能停用时关闭窗口, 订阅随窗口的打开和关闭挂载与释放.
        this.window.bind(state, window -> {
            if (state.get() != FeatureState.ENABLED) {
                window.close();
            }
        });

        // 提交前核对原物品身份, 停用后不再接受编辑.
        this.contents.subscribePreUpdate(event -> {
            if (!this.validateSource()) {
                event.setCancelled(true);
                this.window.close();
            }
        });

        // 每次事务完成后立即写回, 关闭窗口时无需额外保存.
        this.contents.subscribePostUpdate(event -> {
            if (this.validateSource()) {
                if (VersionHelper.isOrAbove26_1()) writeTemplates(shulker, event.slotChanges());
                else writeContents(this.shulker, event.slotChanges());
                this.playerInventory.setChanged();
            }
        });
    }

    private boolean validateSource() {
        if (this.invalidated) return false;
        if (this.playerInventory.getItem(this.sourceSlot) == this.shulker) return true;
        this.invalidated = true;
        this.contents.frozen(true);
        this.window.close();
        return false;
    }

    @NotNull
    private static org.bukkit.inventory.ItemStack[] readContents(@NotNull ItemStack shulker) {
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        org.bukkit.inventory.ItemStack[] contents = new org.bukkit.inventory.ItemStack[items.size()];
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack item = items.get(slot);
            contents[slot] = item.isEmpty() ? null : CraftItemStack.asCraftMirror(item);
        }
        return contents;
    }

    // For 1.21.11
    static void writeContents(@NotNull ItemStack shulker, @NotNull List<SlotChange> changes) {
        ItemContainerContents previous = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        List<?> previousItems = ItemContainerContentsProxy.INSTANCE.getItems(previous);
        ItemStack[] items = new ItemStack[27];
        Arrays.fill(items, ItemStack.EMPTY);
        // 未变化的物品沿用旧组件的只读引用, 每次提交使用新的槽位数组.
        int previousSize = Math.min(items.length, previousItems.size());
        for (int slot = 0; slot < previousSize; slot++) {
            items[slot] = (ItemStack) previousItems.get(slot);
        }
        int changeCount = changes.size();
        for (int i = 0; i < changeCount; i++) {
            SlotChange change = changes.get(i);
            org.bukkit.inventory.ItemStack after = change.unsafeAfter();
            items[change.slot()] = after == null ? ItemStack.EMPTY : ((ItemStack) ItemUtils.getItemStackHandle(after)).copy();
        }
        // 保持原版尾部空槽裁剪, 全空时复用 EMPTY.
        int size = items.length;
        while (size > 0 && items[size - 1].isEmpty()) {
            size--;
        }
        ItemContainerContents updated = size == 0 ? ItemContainerContents.EMPTY
                : (ItemContainerContents) ItemContainerContentsProxy.INSTANCE.create(NonNullList.of(ItemStack.EMPTY, size == items.length ? items : Arrays.copyOf(items, size)));
        // 构造器接收已准备好的独立列表, 发布后不再修改其中的物品.
        shulker.set(DataComponents.CONTAINER, updated);
    }

    // For 26.1+
    private static void writeTemplates(@NotNull ItemStack shulker, @NotNull List<SlotChange> changes) {
        ItemContainerContents previous = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        List<?> previousItems = ItemContainerContentsProxy.INSTANCE.getItems(previous);
        Optional<?>[] items = new Optional<?>[27];
        Arrays.fill(items, Optional.empty());
        // 26.1 起容器保存不可变物品模板, 未变化的槽位直接复用模板.
        int previousSize = Math.min(items.length, previousItems.size());
        for (int slot = 0; slot < previousSize; slot++) {
            items[slot] = (Optional<?>) previousItems.get(slot);
        }
        int changeCount = changes.size();
        for (int i = 0; i < changeCount; i++) {
            SlotChange change = changes.get(i);
            org.bukkit.inventory.ItemStack after = change.unsafeAfter();
            items[change.slot()] = after == null ? Optional.empty()
                    : Optional.of(ItemStackTemplateProxy.INSTANCE.fromNonEmptyStack(ItemUtils.getItemStackHandle(after)));
        }
        int size = items.length;
        while (size > 0 && items[size - 1].isEmpty()) {
            size--;
        }
        ItemContainerContents updated = size == 0 ? ItemContainerContents.EMPTY
                : (ItemContainerContents) ItemContainerContentsProxy.INSTANCE.create$0(Arrays.asList(size == items.length ? items : Arrays.copyOf(items, size)));
        shulker.set(DataComponents.CONTAINER, updated);
    }
}

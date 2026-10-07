package net.momirealms.sparrow.feature.ban.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTexts;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.proxy.bukkit.inventory.CraftItemStackProxy;
import net.momirealms.sparrow.ui.item.Item;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.DateTimeUtils;
import org.bukkit.Material;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class BanMenuItems {
    static final Item FILLER;

    static {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.empty());
        FILLER = Item.simple(CraftItemStackProxy.INSTANCE.asBukkitMirror(stack));
    }

    private BanMenuItems() {
    }

    @NotNull
    static org.bukkit.inventory.ItemStack icon(
            @NotNull SparrowPlayer viewer,
            @NotNull Material material,
            @NotNull Component name,
            @NotNull List<Component> lore
    ) {
        ItemStack stack = new ItemStack(CraftMagicNumbers.getItem(material));
        Component title = viewer.render(name).decoration(TextDecoration.ITALIC, false);
        stack.set(DataComponents.CUSTOM_NAME, CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title)));
        List<net.minecraft.network.chat.Component> lines = new ArrayList<>(lore.size());
        int size = lore.size();
        for (int i = 0; i < size; i++) {
            Component line = viewer.render(lore.get(i)).decoration(TextDecoration.ITALIC, false);
            lines.add(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(line)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return CraftItemStackProxy.INSTANCE.asBukkitMirror(stack);
    }

    @NotNull
    static List<Component> details(@NotNull BanRecord record, long now) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("ban.gui.id", BanTexts.id(record.id())));
        lines.add(Component.translatable("ban.gui.status", BanTexts.status(record, now)));
        if (record.player() != null) {
            lines.add(Component.translatable("ban.gui.player", Component.text(String.valueOf(record.playerName()))));
            lines.add(Component.translatable("ban.gui.uuid", Component.text(record.player().toString())));
        }
        if (record.ip() != null) {
            lines.add(Component.translatable("ban.gui.ip", Component.text(record.ip().toString())));
        }
        lines.add(Component.translatable("ban.gui.reason", BanTexts.reason(record.reason())));
        lines.add(Component.translatable("ban.gui.operator", Component.text(record.operatorName())));
        lines.add(Component.translatable("ban.gui.server", Component.text(record.server())));
        lines.add(Component.translatable("ban.gui.created", Component.text(DateTimeUtils.fullTime(record.createdAt()))));
        lines.add(Component.translatable("ban.gui.expires", BanTexts.expiry(record.expiresAt(), now)));
        if (record.revokedAt() != 0) {
            lines.add(Component.translatable(
                    "command.ban-history.revoked",
                    Component.text(String.valueOf(record.revokedBy())),
                    Component.text(DateTimeUtils.fullTime(record.revokedAt()))
            ));
        }
        return lines;
    }

    static boolean allowed(@NotNull Player viewer, @NotNull String commandId) {
        CommandFeature command = SparrowPlugin.instance().commandManager().feature(commandId);
        if (command == null || !command.isAvailable()) {
            return false;
        }
        CommandConfig config = command.commandConfig();
        String permission = config.getPermission();
        if (!config.isEnable() || (permission != null && !permission.isEmpty() && !viewer.hasPermission(permission))) {
            return false;
        }
        List<String> usages = config.getUsages();
        int size = usages.size();
        for (int i = 0; i < size; i++) {
            if (usages.get(i).trim().startsWith("/")) {
                return true;
            }
        }
        return false;
    }

    static void executeCommand(@NotNull Player viewer, @NotNull String commandId, @NotNull String arguments) {
        List<String> usages = SparrowPlugin.instance().commandManager().feature(commandId).commandConfig().getUsages();
        int size = usages.size();
        for (int i = 0; i < size; i++) {
            String usage = usages.get(i).trim();
            if (usage.startsWith("/")) {
                viewer.performCommand(usage.substring(1) + " " + arguments);
                return;
            }
        }
    }
}
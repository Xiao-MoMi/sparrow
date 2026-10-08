package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.EnchantmentParser;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

public final class EnchantCommand extends BukkitCommandFeature {

    public EnchantCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", MultipleEntitySelectorParser.multipleEntitySelectorParser())
                .required("enchantment", EnchantmentParser.enchantmentParser())
                .optional("level", IntegerParser.integerParser())
                .flag(manager.flagBuilder("slot").withComponent(EnumParser.enumParser(EquipmentSlot.class)))
                .flag(manager.flagBuilder("check"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        MultipleEntitySelector selector = context.get("targets");
        Collection<Entity> entities = selector.values();
        if (entities.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        org.bukkit.enchantments.Enchantment enchantment = context.get("enchantment");
        Holder<Enchantment> minecraftEnchantment = CraftEnchantment.bukkitToMinecraftHolder(enchantment);
        int level = context.getOrDefault("level", 1);
        EquipmentSlot slot = context.flags().getValue("slot", EquipmentSlot.HAND);
        // --check 按原版 /enchant 校验, 移除附魔时不校验
        boolean check = context.flags().hasFlag("check") && level >= 0;
        if (check && level > enchantment.getMaxLevel()) {
            this.handleFeedback(
                    context,
                    MessageConstants.COMMAND_ENCHANT_LEVEL,
                    Component.text(level),
                    Component.text(enchantment.getKey().toString()),
                    Component.text(enchantment.getMaxLevel())
            );
            return;
        }
        for (Entity entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                        if (!(entity instanceof LivingEntity livingEntity)) {
                            this.handleFeedback(
                                    context,
                                    (entity == context.sender() ? MessageConstants.COMMAND_ENCHANT_ENTITY_SELF
                                            : MessageConstants.COMMAND_ENCHANT_ENTITY),
                                    Component.text(entity.getName())
                            );
                            return;
                        }
                        EntityEquipment equipment = livingEntity.getEquipment();
                        if (equipment == null) {
                            this.handleFeedback(
                                    context,
                                    (entity == context.sender() ? MessageConstants.COMMAND_ENCHANT_ENTITY_SELF
                                            : MessageConstants.COMMAND_ENCHANT_ENTITY),
                                    Component.text(entity.getName())
                            );
                            return;
                        }
                        ItemStack item = CraftItemStack.asNMSCopy(equipment.getItem(slot));
                        if (item.isEmpty()) {
                            this.handleFeedback(
                                    context,
                                    (entity == context.sender() ? MessageConstants.COMMAND_ENCHANT_ITEMLESS_SELF
                                            : MessageConstants.COMMAND_ENCHANT_ITEMLESS),
                                    Component.text(entity.getName())
                            );
                            return;
                        }
                        DataComponentType<ItemEnchantments> component = item.is(Items.ENCHANTED_BOOK)
                                ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
                        if (check && !applicable(minecraftEnchantment, item, item.getOrDefault(component, ItemEnchantments.EMPTY))) {
                            this.handleFeedback(
                                    context,
                                    (entity == context.sender() ? MessageConstants.COMMAND_ENCHANT_INCOMPATIBLE_SELF
                                            : MessageConstants.COMMAND_ENCHANT_INCOMPATIBLE),
                                    Component.text(entity.getName()),
                                    Component.text(enchantment.getKey().toString())
                            );
                            return;
                        }
                        if (level < 0) {
                            setEnchantment(item, DataComponents.ENCHANTMENTS, minecraftEnchantment, 0);
                            if (item.is(Items.ENCHANTED_BOOK)) {
                                setEnchantment(item, DataComponents.STORED_ENCHANTMENTS, minecraftEnchantment, 0);
                            }
                        } else {
                            setEnchantment(item, component, minecraftEnchantment, level);
                        }
                        equipment.setItem(slot, CraftItemStack.asBukkitCopy(item));
                        boolean self = entity == context.sender();
                        TranslatableComponent message = level < 0
                                ? (self ? MessageConstants.COMMAND_ENCHANT_REMOVED_SELF : MessageConstants.COMMAND_ENCHANT_REMOVED)
                                : (level == 0 ? (self ? MessageConstants.COMMAND_ENCHANT_ZERO_SELF : MessageConstants.COMMAND_ENCHANT_ZERO)
                                : (self ? MessageConstants.COMMAND_ENCHANT_SUCCESS_SELF : MessageConstants.COMMAND_ENCHANT_SUCCESS));
                        this.handleFeedback(
                                context,
                                message,
                                Component.text(entity.getName()),
                                Component.text(enchantment.getKey().toString()),
                                Component.text(level)
                        );
                    }, () -> {}, entity);
        }
    }

    // 物品需在附魔的适用范围内, 且与已有附魔都不冲突. 已有同一附魔也视为冲突.
    private static boolean applicable(Holder<Enchantment> enchantment, ItemStack item, ItemEnchantments existing) {
        if (!enchantment.value().canEnchant(item)) {
            return false;
        }
        for (Holder<Enchantment> other : existing.keySet()) {
            if (!Enchantment.areCompatible(enchantment, other)) {
                return false;
            }
        }
        return true;
    }

    private static void setEnchantment(ItemStack item, DataComponentType<ItemEnchantments> component, Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(item.getOrDefault(component, ItemEnchantments.EMPTY));
        enchantments.set(enchantment, level);
        item.set(component, enchantments.toImmutable());
    }

    @Override
    public String getFeatureID() {
        return "enchant";
    }
}
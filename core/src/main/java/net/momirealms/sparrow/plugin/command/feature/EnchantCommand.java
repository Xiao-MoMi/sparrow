package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.EnchantmentParser;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public final class EnchantCommand extends BukkitCommandFeature {
    public EnchantCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("targets", MultipleEntitySelectorParser.multipleEntitySelectorParser())
                .required("enchantment", EnchantmentParser.enchantmentParser())
                .optional("level", IntegerParser.integerParser())
                .flag(manager.flagBuilder("slot").withComponent(EnumParser.enumParser(EquipmentSlot.class)))
                .flag(manager.flagBuilder("check"))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        MultipleEntitySelector selector = context.get("targets");
        Collection<Entity> entities = selector.values();
        if (entities.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        Enchantment enchantment = context.get("enchantment");
        int level = context.getOrDefault("level", 1);
        EquipmentSlot slot = context.flags().getValue("slot", EquipmentSlot.HAND);
        // --check 按原版 /enchant 校验, 移除附魔时不校验
        boolean check = context.flags().hasFlag("check") && level >= 0;
        if (check && level > enchantment.getMaxLevel()) {
            this.handleFeedback(context, MessageConstants.COMMAND_ENCHANT_LEVEL,
                    Component.text(level), Component.text(enchantment.getKey().toString()), Component.text(enchantment.getMaxLevel()));
            return;
        }
        for (Entity entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                if (!(entity instanceof LivingEntity livingEntity)) {
                    this.handleFeedback(context, MessageConstants.COMMAND_ENCHANT_ENTITY, Component.text(entity.getName()));
                    return;
                }
                EntityEquipment equipment = livingEntity.getEquipment();
                if (equipment == null) {
                    this.handleFeedback(context, MessageConstants.COMMAND_ENCHANT_ENTITY, Component.text(entity.getName()));
                    return;
                }
                ItemStack item = equipment.getItem(slot);
                if (item.getType().isAir() || item.getAmount() <= 0) {
                    this.handleFeedback(context, MessageConstants.COMMAND_ENCHANT_ITEMLESS, Component.text(entity.getName()));
                    return;
                }
                ItemMeta meta = item.getItemMeta();
                if (check && !applicable(enchantment, item, meta)) {
                    this.handleFeedback(context, MessageConstants.COMMAND_ENCHANT_INCOMPATIBLE, Component.text(entity.getName()), Component.text(enchantment.getKey().toString()));
                    return;
                }
                if (level < 0) {
                    meta.removeEnchant(enchantment);
                    if (meta instanceof EnchantmentStorageMeta storage) {
                        storage.removeStoredEnchant(enchantment);
                    }
                } else if (meta instanceof EnchantmentStorageMeta storage) {
                    storage.addStoredEnchant(enchantment, level, true);
                } else {
                    meta.addEnchant(enchantment, level, true);
                }
                item.setItemMeta(meta);
                equipment.setItem(slot, item);
                this.handleFeedback(context, level < 0 ? MessageConstants.COMMAND_ENCHANT_REMOVED : level == 0 ? MessageConstants.COMMAND_ENCHANT_ZERO : MessageConstants.COMMAND_ENCHANT_SUCCESS,
                        Component.text(entity.getName()), Component.text(enchantment.getKey().toString()), Component.text(level));
            }, () -> {}, entity);
        }
    }

    @Override
    public String getFeatureID() {
        return "enchant";
    }

    // 做原版一致的检查和, 物品需在附魔的适用范围内, 且与已有附魔都不冲突, 已有同一附魔也视为冲突. 附魔书不在任何附魔的适用范围内
    private static boolean applicable(Enchantment enchantment, ItemStack item, ItemMeta meta) {
        if (!enchantment.canEnchantItem(item)) return false;
        Set<Enchantment> existing = meta instanceof EnchantmentStorageMeta storage ? storage.getStoredEnchants().keySet() : meta.getEnchants().keySet();
        for (Enchantment other : existing) {
            if (enchantment.conflictsWith(other)) return false;
        }
        return true;
    }
}

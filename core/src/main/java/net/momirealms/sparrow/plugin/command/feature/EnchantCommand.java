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
}

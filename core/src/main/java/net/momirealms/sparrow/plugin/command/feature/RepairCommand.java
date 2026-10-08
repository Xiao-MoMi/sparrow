package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.minecraft.world.item.ItemStack;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class RepairCommand extends BukkitCommandFeature {

    public RepairCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .flag(manager.flagBuilder("slot").withComponent(EnumParser.enumParser(EquipmentSlot.class)))
                .flag(manager.flagBuilder("value").withComponent(IntegerParser.integerParser(0)))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.get("player");
        EquipmentSlot slot = context.flags().getValue("slot", EquipmentSlot.HAND);
        Integer value = context.flags().getValue("value", null);
        this.plugin().scheduler().platform().run(() -> {
                EntityEquipment equipment = player.getEquipment();
                ItemStack item = CraftItemStack.asNMSCopy(equipment.getItem(slot));
                if (item.isEmpty()) {
                    this.handleFeedback(context, MessageConstants.COMMAND_REPAIR_ITEMLESS, Component.text(player.getName()), Component.text(slot.name()));
                    return;
                }
                int maxDamage = item.getMaxDamage();
                if (maxDamage == 0) {
                    this.handleFeedback(context, MessageConstants.COMMAND_REPAIR_NOT_DAMAGEABLE, Component.text(player.getName()), Component.text(slot.name()));
                    return;
                }
                int oldDamage = item.getDamageValue();
                int damage = value == null ? 0 : Math.max(0, oldDamage - value);
                item.setDamageValue(damage);
                equipment.setItem(slot, CraftItemStack.asBukkitCopy(item));
                this.handleFeedback(
                        context,
                        MessageConstants.COMMAND_REPAIR_SUCCESS,
                        Component.text(player.getName()),
                        Component.text(slot.name()),
                        Component.text(oldDamage - damage),
                        Component.text(maxDamage - damage),
                        Component.text(maxDamage)
                );
            },
            () -> {},
            player
        );
    }

    @Override
    public String getFeatureID() {
        return "repair";
    }
}

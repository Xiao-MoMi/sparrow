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
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

public final class ItemDamageCommand extends BukkitCommandFeature {

    public ItemDamageCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .required("value", IntegerParser.integerParser(0))
                .handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        Player player = context.get("player");
        int value = context.get("value");
        this.plugin().scheduler().platform().run(() -> {
            ItemStack item = CraftItemStack.asNMSCopy(player.getInventory().getItemInMainHand());
            if (item.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_DAMAGE_ITEMLESS, Component.text(player.getName()));
                return;
            }
            int maxDamage = item.getMaxDamage();
            if (maxDamage == 0) {
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_DAMAGE_NOT_DAMAGEABLE, Component.text(player.getName()));
                return;
            }
            if (value >= maxDamage) {
                player.getInventory().setItemInMainHand(CraftItemStack.asBukkitCopy(ItemStack.EMPTY));
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_DAMAGE_BROKEN, Component.text(player.getName()));
                return;
            }
            item.setDamageValue(value);
            player.getInventory().setItemInMainHand(CraftItemStack.asBukkitCopy(item));
            this.handleFeedback(
                    context,
                    MessageConstants.COMMAND_ITEM_DAMAGE_SUCCESS,
                    Component.text(player.getName()),
                    Component.text(value),
                    Component.text(maxDamage - value),
                    Component.text(maxDamage)
            );
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "item-damage";
    }
}
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
import org.bukkit.inventory.PlayerInventory;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

public final class RepairAllCommand extends BukkitCommandFeature {

    public RepairAllCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser()).handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        Player player = context.get("player");
        this.plugin().scheduler().platform().run(() -> {
            PlayerInventory inventory = player.getInventory();
            int repaired = 0;
            long restored = 0;
            for (int slot = 0, size = inventory.getSize(); slot < size; slot++) {
                ItemStack item = CraftItemStack.asNMSCopy(inventory.getItem(slot));
                if (item.isEmpty() || item.getMaxDamage() == 0 || item.getDamageValue() == 0) continue;
                restored += item.getDamageValue();
                item.setDamageValue(0);
                inventory.setItem(slot, CraftItemStack.asBukkitCopy(item));
                repaired++;
            }
            this.handleFeedback(
                    context,
                    MessageConstants.COMMAND_REPAIR_ALL_SUCCESS,
                    Component.text(player.getName()),
                    Component.text(repaired),
                    Component.text(restored)
            );
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "repair-all";
    }
}

package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

public final class ClearInvCommand extends BukkitCommandFeature {

    public ClearInvCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser()).handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        Player player = context.get("player");
        this.plugin().scheduler().platform().run(() -> {
            // 关闭界面会把合成材料和鼠标物品放回背包或掉到地上.
            InventoryView view = player.getOpenInventory();
            if (view.getType() == InventoryType.CRAFTING || view.getType() == InventoryType.WORKBENCH) {
                view.getTopInventory().clear();
            }
            player.setItemOnCursor(null);
            player.closeInventory();
            player.getInventory().clear();
            this.handleFeedback(context, MessageConstants.COMMAND_CLEAR_INV_SUCCESS, Component.text(player.getName()));
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "clear-inv";
    }
}
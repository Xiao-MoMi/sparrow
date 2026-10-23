package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.message.OpenContainerMessage.ContainerType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.jetbrains.annotations.NotNull;

public final class CartographyTableCommand extends AbstractContainerCommand {

    public CartographyTableCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(
                commandManager,
                plugin,
                "cartography_table",
                ContainerType.CARTOGRAPHY_TABLE,
                MessageConstants.COMMAND_CARTOGRAPHY_TABLE_SUCCESS_SELF,
                MessageConstants.COMMAND_CARTOGRAPHY_TABLE_SUCCESS
        );
    }
}
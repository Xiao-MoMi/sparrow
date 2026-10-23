package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.message.OpenContainerMessage.ContainerType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.jetbrains.annotations.NotNull;

public final class SmithingTableCommand extends AbstractContainerCommand {

    public SmithingTableCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(
                commandManager,
                plugin,
                "smithing-table",
                ContainerType.SMITHING_TABLE,
                MessageConstants.COMMAND_SMITHING_TABLE_SUCCESS_SELF,
                MessageConstants.COMMAND_SMITHING_TABLE_SUCCESS
        );
    }
}
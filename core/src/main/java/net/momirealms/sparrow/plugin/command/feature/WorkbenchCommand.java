package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.message.OpenContainerMessage.ContainerType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.jetbrains.annotations.NotNull;

public final class WorkbenchCommand extends AbstractContainerCommand {

    public WorkbenchCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(
                commandManager,
                plugin,
                "workbench",
                ContainerType.WORKBENCH,
                MessageConstants.COMMAND_WORKBENCH_SUCCESS_SELF,
                MessageConstants.COMMAND_WORKBENCH_SUCCESS
        );
    }
}
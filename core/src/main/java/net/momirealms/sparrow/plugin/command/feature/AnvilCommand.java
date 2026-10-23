package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.message.OpenContainerMessage.ContainerType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.jetbrains.annotations.NotNull;

public final class AnvilCommand extends AbstractContainerCommand {

    public AnvilCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(
                commandManager,
                plugin,
                "anvil",
                ContainerType.ANVIL,
                MessageConstants.COMMAND_ANVIL_SUCCESS_SELF,
                MessageConstants.COMMAND_ANVIL_SUCCESS
        );
    }
}
package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.message.OpenContainerMessage.ContainerType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.jetbrains.annotations.NotNull;

public final class EnchantmentTableCommand extends AbstractContainerCommand {

    public EnchantmentTableCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(
                commandManager,
                plugin,
                "enchantment_table",
                ContainerType.ENCHANTMENT_TABLE,
                MessageConstants.COMMAND_ENCHANTMENT_TABLE_SUCCESS_SELF,
                MessageConstants.COMMAND_ENCHANTMENT_TABLE_SUCCESS
        );
    }
}
package net.momirealms.sparrow.feature.highlight;

import net.minecraft.SharedConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class HighlightCommandTest {
    @Test
    void registersCommandWithCloudFlagAliasValidation() {
        SharedConstants.tryDetectVersion();
        org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return true;
            }
        };
        HighlightCommand command = new HighlightCommand(mock(CommandManager.class), mock(SparrowPlugin.class));
        assertDoesNotThrow(() -> command.registerCommand(manager, Command.newBuilder("highlight", CommandMeta.empty())));
    }
}

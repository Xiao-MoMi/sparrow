package net.momirealms.sparrow.plugin.command.feature;

import net.minecraft.SharedConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class HighlightCommandTest {
    @Test
    @SuppressWarnings("unchecked")
    void buildsCommandWithCloudFlagAliasValidation() {
        SharedConstants.tryDetectVersion();
        org.incendo.cloud.CommandManager<CommandSender> manager = mock(org.incendo.cloud.CommandManager.class, CALLS_REAL_METHODS);
        HighlightCommand command = new HighlightCommand(mock(CommandManager.class), mock(SparrowPlugin.class));
        assertDoesNotThrow(() -> command.assembleCommand(manager, Command.<CommandSender>newBuilder("highlight", CommandMeta.empty())).build());
    }
}

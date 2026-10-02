package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.SparrowYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandsConfigTest {
    @TempDir
    Path directory;

    @Test
    void createsTheWarpPanelCommandsWithDefaultPermissionsAndAliases() {
        CommandsConfig config = new CommandsConfig(this.directory, SparrowYaml.builder().build());
        var edit = config.configDefinition().command("edit-warp");
        assertTrue(edit.isEnable());
        assertEquals("sparrow.command.edit-warp", edit.getPermission());
        assertEquals(List.of("/sparrow edit-warp", "/edit-warp"), edit.getUsages());
        assertEquals(List.of("/sparrow warp-list", "/warp-list"), config.configDefinition().command("warp-list").getUsages());
    }

    @Test
    void upgradingAddsEditWarpAndPreservesCustomizedExistingCommands() throws Exception {
        Files.writeString(this.directory.resolve("commands.yml"), """
                __version__: '3'
                warp-list:
                  enable: true
                  permission: players.places
                  usages: [/places]
                warp:
                  enable: false
                  permission: players.travel
                  usages: [/travel]
                """);
        CommandsConfig config = new CommandsConfig(this.directory, SparrowYaml.builder().build());
        assertEquals(DependencyVersions.COMMANDS_CONFIG_VERSION, config.configDefinition().configVersion);
        assertEquals(List.of("/places"), config.configDefinition().command("warp-list").getUsages());
        assertEquals("players.places", config.configDefinition().command("warp-list").getPermission());
        assertFalse(config.configDefinition().command("warp").isEnable());
        assertEquals(List.of("/travel"), config.configDefinition().command("warp").getUsages());
        assertTrue(config.configDefinition().command("edit-warp").isEnable());
        assertTrue(Files.readString(this.directory.resolve("commands.yml")).contains("edit-warp:"));
    }
}

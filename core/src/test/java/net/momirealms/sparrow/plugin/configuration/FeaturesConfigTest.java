package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeaturesConfigTest {
    @TempDir
    Path directory;

    @Test
    void generatesSeparateTopLevelSections() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().setAllowDuplicateKeys(false).build();
        FeaturesConfig config = new FeaturesConfig(this.directory, yaml);
        YamlDocument document = yaml.load(this.directory.resolve("features.yml"));

        assertEquals("1", document.get(String.class, Route.from("__version__")));
        assertFalse(document.contains(Route.from("version")));
        assertFalse(document.contains(Route.from("features")));
        assertTrue(config.config().quickShulker().enabled());
        assertTrue(config.config().quickShulker().requireSneaking());
        assertTrue(config.config().quickShulker().allowOffhand());
        assertEquals(0, config.config().quickShulker().disabledWorlds().size());
        assertFalse(Files.exists(this.directory.resolve("config.yml")));
    }

    @Test
    void switchingPreservesPendingEditsAndCommentsUntilReload() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().setAllowDuplicateKeys(false).build();
        FeaturesConfig config = new FeaturesConfig(this.directory, yaml);
        Path path = this.directory.resolve("features.yml");
        String edited = Files.readString(path)
                .replace("require-sneaking: true", "require-sneaking: false # keep this comment")
                .replace("disabled-worlds: []", "disabled-worlds: [world_nether]");
        Files.writeString(path, edited);

        config.saveEnabled("quick-shulker", true);
        assertTrue(config.config().quickShulker().enabled());
        assertTrue(config.config().quickShulker().requireSneaking());
        assertTrue(Files.readString(path).contains("keep this comment"));
        config.reload();
        FeaturesConfig.ConfigDefinition reloaded = config.config();
        assertTrue(reloaded.quickShulker().enabled());
        assertFalse(reloaded.quickShulker().requireSneaking());
        assertEquals(List.of("world_nether"), reloaded.quickShulker().disabledWorlds());

        FeaturesConfig restarted = new FeaturesConfig(this.directory, yaml);
        assertTrue(restarted.config().quickShulker().enabled());
        assertFalse(restarted.config().quickShulker().requireSneaking());
    }

    @Test
    void malformedReloadRetainsThePublishedSnapshot() throws Exception {
        FeaturesConfig config = new FeaturesConfig(this.directory, SparrowYaml.builder().build());
        FeaturesConfig.ConfigDefinition original = config.config();
        Files.writeString(this.directory.resolve("features.yml"), "__version__: 1\nquick-shulker: [\n");

        assertThrows(RuntimeException.class, config::reload);
        assertSame(original, config.config());
    }

    @Test
    void upgradingCommandsPreservesExistingRegistrationSettings() throws Exception {
        Files.writeString(this.directory.resolve("commands.yml"), """
                __version__: '1'
                reload:
                  enable: false
                  permission: custom.reload
                  usages: [/custom reload]
                """);
        CommandsConfig commands = new CommandsConfig(this.directory, SparrowYaml.builder().build());

        assertFalse(commands.configDefinition().command("reload").isEnable());
        assertEquals("custom.reload", commands.configDefinition().command("reload").getPermission());
        assertTrue(commands.configDefinition().command("feature_enable").isEnable());
        assertTrue(commands.configDefinition().command("feature_disable").isEnable());
        assertTrue(commands.configDefinition().command("feature_status").isEnable());
        assertTrue(commands.configDefinition().command("features").isEnable());
        YamlDocument document = SparrowYaml.builder().build().load(this.directory.resolve("commands.yml"));
        assertEquals(DependencyVersions.COMMANDS_CONFIG_VERSION, document.get(String.class, Route.from("__version__")));
        assertFalse(document.contains(Route.from("config-version")));
    }
}

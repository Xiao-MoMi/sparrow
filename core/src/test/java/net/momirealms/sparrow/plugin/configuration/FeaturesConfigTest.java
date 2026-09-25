package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.yaml.SparrowYaml;
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
}

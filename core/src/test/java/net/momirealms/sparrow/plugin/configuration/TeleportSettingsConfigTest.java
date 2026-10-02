package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.route.Route;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TeleportSettingsConfigTest {
    @TempDir
    Path directory;

    @Test
    void upgradeAddsBackAndBedDefaultsAndPreservesExistingSettings() throws Exception {
        Files.writeString(this.directory.resolve("features.yml"), """
                __version__: '3'
                back:
                  record-death: false
                  server-switch-window-seconds: 45
                """);
        FeaturesConfig features = new FeaturesConfig(this.directory, SparrowYaml.builder().build());
        assertFalse(features.config().back().recordDeath());
        assertEquals(45, features.config().back().serverSwitchWindowSeconds());
        assertEquals(new TeleportOptions(TeleportType.BACK, 3, 0, true, true), features.config().back().teleportOptions());
        assertTrue(features.config().bed().enabled());
        assertSame(features.config().bed(), features.config().settings("bed"));
        assertEquals(new TeleportOptions(TeleportType.BED, 3, 0, true, true), features.config().bed().teleportOptions());
        String featuresYaml = Files.readString(this.directory.resolve("features.yml"));
        assertTrue(featuresYaml.contains("warmup-seconds: 3"));
        assertTrue(featuresYaml.contains("bed:"));
        assertEquals(DependencyVersions.FEATURES_CONFIG_VERSION, SparrowYaml.builder().build().load(this.directory.resolve("features.yml")).getString(Route.from("__version__")));
    }

    @Test
    void reloadUsesCustomWarmupCooldownAndCancellationSettings() throws Exception {
        FeaturesConfig features = new FeaturesConfig(this.directory, SparrowYaml.builder().build());
        Files.writeString(this.directory.resolve("features.yml"), """
                __version__: '4'
                back:
                  warmup-seconds: 7
                  cooldown-seconds: 30
                  cancel-on-move: true
                  cancel-on-damage: false
                bed:
                  enabled: false
                  warmup-seconds: 5
                  cooldown-seconds: 20
                  cancel-on-move: false
                  cancel-on-damage: true
                """);
        features.reload();
        assertEquals(new TeleportOptions(TeleportType.BACK, 7, 30, true, false), features.config().back().teleportOptions());
        assertFalse(features.config().bed().enabled());
        assertEquals(new TeleportOptions(TeleportType.BED, 5, 20, false, true), features.config().bed().teleportOptions());
        features.saveEnabled("bed", true);
        features.reload();
        assertTrue(features.config().bed().enabled());
        assertEquals(new TeleportOptions(TeleportType.BED, 5, 20, false, true), features.config().bed().teleportOptions());
    }
}

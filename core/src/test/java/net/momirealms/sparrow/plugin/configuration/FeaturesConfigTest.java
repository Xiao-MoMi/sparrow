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

    @Test
    void headSettingsRoundTripCustomUrlsHeadersAndCacheDurations() throws Exception {
        Files.writeString(this.directory.resolve("features.yml"), """
                __version__: '1'
                head:
                  enabled: true
                  source-order: [api, online]
                  request-timeout: 20s
                  cache:
                    memory:
                      ttl: 2m30s
                    redis:
                      enabled: true
                      ttl: 3d
                  api:
                    name-url: 'https://example.test/names/{name}'
                    profile-url: 'https://example.test/profiles/{uuid-dashed}'
                    headers:
                      Authorization: Bearer example
                """);
        FeaturesConfig config = new FeaturesConfig(this.directory, SparrowYaml.builder().build());
        var head = config.config().head();
        assertEquals(List.of("api", "online"), head.sourceOrder());
        assertEquals("2m30s", head.cache().memory().ttl());
        assertEquals("3d", head.cache().redis().ttl());
        assertEquals("https://example.test/profiles/{uuid-dashed}", head.api().profileUrl());
        assertEquals("Bearer example", head.api().headers().get("Authorization"));
        config.saveEnabled("head", false);
        config.reload();
        assertFalse(config.config().head().enabled());
        assertEquals("Bearer example", config.config().head().api().headers().get("Authorization"));
    }
}

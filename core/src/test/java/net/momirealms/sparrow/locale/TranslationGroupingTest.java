package net.momirealms.sparrow.locale;

import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.plugin.configuration.ConfigurationManager;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.route.Route;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TranslationGroupingTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"en", "zh_cn"})
    void upgradingPreservesTemplateGroupsAndCustomizedMessages(String language) throws Exception {
        Plugin plugin = mock(Plugin.class);
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        SparrowYaml yaml = SparrowYaml.builder().setAllowDuplicateKeys(false).build();
        when(plugin.dataFolderPath()).thenReturn(this.directory);
        when(plugin.configurationManager()).thenReturn(configuration);
        when(plugin.logger()).thenReturn(mock(PluginLogger.class));
        when(configuration.sparrowYaml()).thenReturn(yaml);
        when(plugin.resourceStream(anyString()))
                .thenAnswer(invocation -> this.getClass().getClassLoader().getResourceAsStream(invocation.getArgument(0)));
        Path file = this.directory.resolve(language + ".yml");
        Files.writeString(file, "__version__: '4'\ncommand.color.query: 'custom translation'\n");
        TranslationManager previous = TranslationManagerImpl.instance;
        TranslationManagerImpl.instance = null;
        try {
            TranslationManagerImpl translations = new TranslationManagerImpl(plugin);
            translations.loadFromFileSystem(this.directory);
            String updated = Files.readString(file);
            assertTrue(updated.contains("# command.color"));
            assertTrue(updated.contains("# command.custom-model-data"));
            assertTrue(updated.contains("# command.item-lore"));
            assertTrue(updated.contains("# command.head"));
            assertTrue(updated.contains("# log.storage"));
            var document = yaml.load(file);
            assertEquals("custom translation", document.getString(Route.from("command.color.query")));
            assertEquals(DependencyVersions.LANG_VERSION, document.getString(Route.from("__version__")));
            assertNotNull(document.getString(Route.from("command.custom-model-data.success")));
            assertNotNull(document.getString(Route.from("command.item-lore.success")));
            assertNotNull(document.getString(Route.from("command.head.timeout")));
        } finally {
            TranslationManagerImpl.instance = previous;
        }
    }
}

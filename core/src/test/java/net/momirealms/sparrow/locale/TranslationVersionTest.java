package net.momirealms.sparrow.locale;

import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.plugin.configuration.ConfigurationManager;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TranslationVersionTest {
    @TempDir
    Path directory;
    private ConfigurationManager configurationManager;

    @Test
    void upgradingTranslationsPreservesMessagesAndExcludesVersionMetadata() throws Exception {
        Files.writeString(this.directory.resolve("config.yml"), "__version__: '" + DependencyVersions.CONFIG_VERSION + "'\nforced-locale: en\n");
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "dataFolderPath" -> this.directory;
                    case "configurationManager" -> this.configurationManager;
                    case "resourceStream" -> this.getClass().getClassLoader().getResourceAsStream((String) arguments[0]);
                    case "saveResource" -> {
                        String resource = (String) arguments[0];
                        Path path = this.directory.resolve(resource);
                        if (!Files.exists(path)) {
                            Files.createDirectories(path.getParent());
                            try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(resource)) {
                                Files.copy(stream, path);
                            }
                        }
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        this.configurationManager = new ConfigurationManager(plugin);
        this.configurationManager.reload();
        Path path = this.directory.resolve("translations/en.yml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "lang-version: '1'\ncommand.feature.busy: 'custom busy message'\n");

        TranslationManager previous = TranslationManagerImpl.instance;
        try {
            TranslationManagerImpl translations = new TranslationManagerImpl(plugin);
            translations.reload();

            YamlDocument document = this.configurationManager.sparrowYaml().load(path);
            assertEquals(DependencyVersions.LANG_VERSION, document.get(String.class, Route.from("__version__")));
            assertFalse(document.contains(Route.from("lang-version")));
            assertEquals("custom busy message", translations.miniMessageTranslation("command.feature.busy", Locale.ENGLISH));
            assertTrue(translations.translationKeys().contains("command.features.header"));
            assertTrue(translations.miniMessageTranslation("command.features.label.enable", Locale.ENGLISH).contains("Enable"));
            assertFalse(translations.translationKeys().contains("__version__"));
            assertFalse(translations.translationKeys().contains("lang-version"));

            YamlDocument generated = this.configurationManager.sparrowYaml().load(this.directory.resolve("translations/zh_cn.yml"));
            assertEquals(DependencyVersions.LANG_VERSION, generated.get(String.class, Route.from("__version__")));
            translations.reload();
            assertFalse(translations.translationKeys().contains("__version__"));
            assertEquals("custom busy message", translations.miniMessageTranslation("command.feature.busy", Locale.ENGLISH));
        } finally {
            TranslationManagerImpl.instance = previous;
        }
    }
}

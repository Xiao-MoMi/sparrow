package net.momirealms.sparrow.common.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import net.momirealms.sparrow.common.plugin.SparrowPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManagerImpl implements ConfigManager {

    private final SparrowPlugin plugin;

    public ConfigManagerImpl(SparrowPlugin plugin) {
        this.plugin = plugin;
    }

    protected Path resolveConfig(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        filePath = filePath.replace('\\', '/');

        Path configFile = plugin.getBootstrap().getConfigDirectory().resolve(filePath);

        // if the config doesn't exist, create it based on the template in the resources dir
        if (!Files.exists(configFile)) {
            try {
                Files.createDirectories(configFile.getParent());
            } catch (IOException e) {
                // ignore
            }

            try (InputStream is = plugin.getBootstrap().getResourceStream(filePath)) {

                if (is == null) {
                    throw new IllegalArgumentException("The embedded resource '" + filePath + "' cannot be found");
                }

                Files.copy(is, configFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return configFile;
    }

    @Override
    public YamlDocument loadConfig(String filePath) {
        return loadConfig(filePath, '.');
    }

    @Override
    public YamlDocument loadConfig(String filePath, char routeSeparator) {
        try {
            return YamlDocument.create(
                    resolveConfig(filePath).toFile(),
                    plugin.getBootstrap().getResourceStream(filePath),
                    GeneralSettings.builder().setRouteSeparator(routeSeparator).build(),
                    LoaderSettings
                            .builder()
                            .setAutoUpdate(true)
                            .build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings
                            .builder()
                            .setVersioning(new BasicVersioning("__version__"))
                            .build()
            );
        } catch (IOException e) {
            plugin.getBootstrap().getPluginLogger().severe("Failed to load config " + filePath, e);
            return null;
        }
    }
}

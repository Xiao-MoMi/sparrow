package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.node.YamlNode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ModulesConfig {

    private ModulesConfig() {
    }

    @NotNull
    public static Set<String> loadEnabled() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        Path file = plugin.dataFolderPath().resolve("modules.yml");
        SparrowYaml yaml = plugin.configurationManager().sparrowYaml();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                yaml.createDocument().save(file);
                return Set.of();
            }
            YamlDocument document = yaml.load(file);
            Set<String> enabled = new HashSet<>();
            for (Map.Entry<Object, YamlNode<?>> entry : document.value().entrySet()) {
                if (!(entry.getKey() instanceof String id) || !(entry.getValue().value() instanceof Boolean value)) {
                    throw new IllegalArgumentException("modules.yml requires feature IDs mapped to booleans: " + entry.getKey());
                }
                if (value) enabled.add(id);
            }
            return Set.copyOf(enabled);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load modules.yml", exception);
        }
    }
}
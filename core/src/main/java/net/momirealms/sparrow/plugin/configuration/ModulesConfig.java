package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.node.YamlNode;
import net.momirealms.sparrow.yaml.route.Route;
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
            boolean changed = !Files.exists(file);
            YamlDocument document = changed ? yaml.createDocument() : yaml.load(file);
            Set<String> enabled = new HashSet<>();
            for (Map.Entry<Object, YamlNode<?>> entry : document.value().entrySet()) {
                if (!(entry.getKey() instanceof String id) || !(entry.getValue().value() instanceof Boolean value)) {
                    throw new IllegalArgumentException("modules.yml requires feature IDs mapped to booleans: " + entry.getKey());
                }
                if (value) enabled.add(id);
            }
            for (String id : plugin.featureManager().ids()) {
                if (document.value().containsKey(id)) continue;
                document.setAndGet(Route.from(id), true);
                enabled.add(id);
                changed = true;
            }
            if (changed) {
                Files.createDirectories(file.getParent());
                document.save(file);
            }
            return Set.copyOf(enabled);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load modules.yml", exception);
        }
    }
}
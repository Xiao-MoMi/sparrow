package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.database.DatabaseType;
import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.yaml.SparrowYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigTest {
    @TempDir
    Path directory;

    @Test
    void generatesStorageOptionsAndKeepsConnectionsFixedOnReload() throws Exception {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("dataFolderPath")) return this.directory;
                    throw new UnsupportedOperationException(method.getName());
                });
        SparrowYaml yaml = SparrowYaml.builder().setAllowDuplicateKeys(false).setAllowObjectKeys(false).build();
        PluginConfig config = new PluginConfig(plugin, yaml);
        config.reload();

        String generated = Files.readString(this.directory.resolve("config.yml"));
        assertTrue(generated.contains("database:"));
        assertTrue(generated.contains("redis:"));
        assertTrue(generated.contains("jdbc:mariadb://localhost:3306/minecraft"));
        assertTrue(generated.contains("jdbc:postgresql://localhost:5432/minecraft"));
        assertTrue(generated.contains("mongodb://localhost:27017"));
        assertTrue(generated.contains("table-prefix: sparrow_"));
        DatabaseType initialType = PluginConfig.database().type();
        DatabaseType changedType = initialType == DatabaseType.POSTGRESQL ? DatabaseType.MYSQL : DatabaseType.POSTGRESQL;

        Files.writeString(this.directory.resolve("config.yml"), generated
                .replace("type: " + initialType, "type: " + changedType)
                .replace("redis://localhost:6379/0", "redis://localhost:6380/0"));
        config.reload();
        assertEquals(initialType, PluginConfig.database().type());
        assertEquals("redis://localhost:6379/0", PluginConfig.redis().url());

        PluginConfig restarted = new PluginConfig(plugin, yaml);
        restarted.reload();
        assertEquals(changedType, PluginConfig.database().type());
        assertEquals("redis://localhost:6380/0", PluginConfig.redis().url());
    }
}

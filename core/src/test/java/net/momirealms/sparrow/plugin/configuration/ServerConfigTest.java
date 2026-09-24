package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.yaml.SparrowYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigTest {
    @TempDir
    Path directory;

    @Test
    void generatesBlankIdAndKeepsStartupIdOnReload() throws Exception {
        SparrowYaml yaml = SparrowYaml.builder().setAllowDuplicateKeys(false).setAllowObjectKeys(false).build();
        Path file = this.directory.resolve("server.yml");
        // 首次生成时 server-id 留空
        ServerConfig config = new ServerConfig(this.directory, yaml);
        config.reload();
        String generated = Files.readString(file);
        assertTrue(generated.contains("server-id: ''"), generated);
        assertEquals("", ServerConfig.serverId());

        // 同一次启动内的重载沿用启动时的值
        Files.writeString(file, generated.replace("server-id: ''", "server-id: lobby"));
        config.reload();
        assertEquals("", ServerConfig.serverId());

        // 重启后读取新值
        ServerConfig restarted = new ServerConfig(this.directory, yaml);
        restarted.reload();
        assertEquals("lobby", ServerConfig.serverId());
    }
}

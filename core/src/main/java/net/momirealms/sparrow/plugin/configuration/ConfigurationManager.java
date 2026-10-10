package net.momirealms.sparrow.plugin.configuration;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.plugin.configuration.serializer.KeySerializer;
import net.momirealms.sparrow.plugin.configuration.serializer.SoundSerializer;
import net.momirealms.sparrow.yaml.SparrowYaml;
import org.jetbrains.annotations.NotNull;

public class ConfigurationManager {
    private final SparrowYaml sparrowYaml;
    private final CommandsConfig commandsConfig;
    private final PluginConfig pluginConfig;
    private final ServerConfig serverConfig;
    private final TeleportConfig teleportConfig;
    private final FeaturesConfig featuresConfig;

    public ConfigurationManager(Plugin plugin) {
        // 基础配置
        this.sparrowYaml = SparrowYaml.builder()
                .setAllowDuplicateKeys(false)
                .setAllowObjectKeys(false)
                .build();
        // 序列化器
        this.sparrowYaml.serializers().register(Key.class, KeySerializer.INSTANCE.serializer());
        this.sparrowYaml.serializers().register(Sound.class, SoundSerializer.INSTANCE.serializer());
        // 配置文件
        this.pluginConfig = new PluginConfig(plugin, this.sparrowYaml);
        this.serverConfig = new ServerConfig(plugin.dataFolderPath(), this.sparrowYaml);
        this.commandsConfig = new CommandsConfig(plugin.dataFolderPath(), this.sparrowYaml);
        this.teleportConfig = new TeleportConfig(plugin.dataFolderPath(), this.sparrowYaml);
        this.featuresConfig = new FeaturesConfig(plugin.dataFolderPath(), this.sparrowYaml);
    }

    /**
     * 重新加载允许运行时更新的配置快照.
     */
    public int reload() {
        int issues = 0;
        this.pluginConfig.reload();
        this.serverConfig.reload();
        issues += this.teleportConfig.reload();
        this.featuresConfig.reload();
        return issues;
    }

    @NotNull
    public SparrowYaml sparrowYaml() {
        return this.sparrowYaml;
    }

    public CommandsConfig commandsConfig() {
        return this.commandsConfig;
    }

    @NotNull
    public FeaturesConfig featuresConfig() {
        return this.featuresConfig;
    }
}

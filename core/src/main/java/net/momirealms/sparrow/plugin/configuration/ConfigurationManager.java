package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.yaml.SparrowYaml;
import org.jetbrains.annotations.NotNull;

public class ConfigurationManager {
    private final SparrowYaml sparrowYaml;
    private final CommandsConfig commandsConfig;
    private final PluginConfig pluginConfig;

    /**
     * 创建共享 YAML 环境并初始化插件配置.
     */
    public ConfigurationManager(Plugin plugin) {
        this.sparrowYaml = SparrowYaml.builder()
                .setAllowDuplicateKeys(false)
                .setAllowObjectKeys(false)
                .build();
        this.pluginConfig = new PluginConfig(plugin, this.sparrowYaml);
        this.commandsConfig = new CommandsConfig(plugin.dataFolderPath(), this.sparrowYaml);
    }

    /**
     * 重新加载允许运行时更新的配置快照.
     */
    public void reload() {
        this.pluginConfig.reload();
    }

    @NotNull
    public SparrowYaml sparrowYaml() {
        return this.sparrowYaml;
    }

    public CommandsConfig commandsConfig() {
        return this.commandsConfig;
    }
}

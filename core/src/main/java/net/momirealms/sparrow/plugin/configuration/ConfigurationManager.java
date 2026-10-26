package net.momirealms.sparrow.plugin.configuration;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.feature.back.BackSettings;
import net.momirealms.sparrow.feature.tpa.TpaSettings;
import net.momirealms.sparrow.feature.rtp.RtpSettings;
import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.plugin.configuration.serializer.KeySerializer;
import net.momirealms.sparrow.plugin.configuration.serializer.SoundSerializer;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import net.momirealms.sparrow.yaml.serializer.SerializerRegistry;
import org.jetbrains.annotations.NotNull;

public class ConfigurationManager {
    private final Plugin plugin;
    private final SparrowYaml sparrowYaml;
    private final CommandsConfig commandsConfig;
    private final PluginConfig pluginConfig;
    private final ServerConfig serverConfig;

    public ConfigurationManager(Plugin plugin) {
        this.plugin = plugin;
        // 基础配置
        this.sparrowYaml = SparrowYaml.builder()
                .setAllowDuplicateKeys(false)
                .setAllowObjectKeys(false)
                .build();
        // 序列化器
        SerializerRegistry serializers = this.sparrowYaml.serializers();
        serializers.register(Key.class, KeySerializer.INSTANCE.serializer());
        serializers.register(Sound.class, SoundSerializer.INSTANCE.serializer());
        NodeSerializer<RtpSettings.WarmupOptions> rtpWarmup = serializers.register(RtpSettings.WarmupOptions.class);
        serializers.registerUnsafe(RtpSettings.WarmupOptions.class, rtpWarmup.withAlternative(NodeSerializers.STRING, RtpSettings.WarmupOptions::of));
        NodeSerializer<BackSettings.WarmupOptions> backWarmup = serializers.register(BackSettings.WarmupOptions.class);
        serializers.registerUnsafe(BackSettings.WarmupOptions.class, backWarmup.withAlternative(NodeSerializers.STRING, BackSettings.WarmupOptions::of));
        NodeSerializer<TpaSettings.WarmupOptions> tpaWarmup = serializers.register(TpaSettings.WarmupOptions.class);
        serializers.registerUnsafe(TpaSettings.WarmupOptions.class, tpaWarmup.withAlternative(NodeSerializers.STRING, TpaSettings.WarmupOptions::of));
        // 配置文件
        this.pluginConfig = new PluginConfig(plugin, this.sparrowYaml);
        this.serverConfig = new ServerConfig(plugin.dataFolderPath(), this.sparrowYaml);
        this.commandsConfig = new CommandsConfig(plugin.dataFolderPath(), this.sparrowYaml);
        this.pluginConfig.reload();
    }

    /**
     * 重新加载允许运行时更新的配置快照.
     */
    public void reload() {
        this.pluginConfig.reload();
        this.plugin.serverManager().reload();
    }

    @NotNull
    public SparrowYaml sparrowYaml() {
        return this.sparrowYaml;
    }

    public CommandsConfig commandsConfig() {
        return this.commandsConfig;
    }
}

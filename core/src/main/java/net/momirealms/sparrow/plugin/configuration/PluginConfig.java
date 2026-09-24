package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import net.momirealms.sparrow.yaml.upgrade.version.FieldVersionExtractor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;

public final class PluginConfig {
    private static final String CONFIG_FILE = "config.yml";
    private static volatile ConfigDefinition config; // 重载会换上完整的新快照, 读取可能发生在不同线程

    private final Path configFilePath;
    private final YamlMapper<ConfigDefinition> configMapper;

    PluginConfig(Plugin plugin, SparrowYaml sparrowYaml) {
        this.configFilePath = plugin.dataFolderPath().resolve(CONFIG_FILE);
        YamlUpgradePipeline upgradePipeline = YamlUpgradePipeline.builder()
                .versionExtractor(new FieldVersionExtractor("config-version"))
                .build();
        YamlMapperFactory mapperFactory = YamlMapperFactory.builder()
                .backupOnUpgrade(true)
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(upgradePipeline)
                .build();
        this.configMapper = mapperFactory.create(ConfigDefinition.class, ConfigDefinition::new);
    }

    void reload() {
        try {
            config = this.configMapper.load(this.configFilePath).value();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + CONFIG_FILE, e);
        }
    }

    // 配置文件
    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class ConfigDefinition {
        @Comment("Do not modify this value")
        @Comment(lang = "zh", value = "配置版本, 请勿修改.")
        String configVersion = DependencyVersions.CONFIG_VERSION;

        @Comment("Enables or disables metrics collection via BStats")
        @Comment(lang = "zh", value = "是否通过 bStats 提交使用统计.")
        boolean metrics = true;

        @Comment("Enables automatic update checks")
        @Comment(lang = "zh", value = "是否自动检查更新.")
        boolean updateChecker = true;

        @Comment("Console language, such as zh_CN or en_US. Leave blank to use the system language.")
        @Comment(lang = "zh", value = "控制台语言, 例如 zh_CN 或 en_US. 留空时跟随系统语言.")
        String forcedLocale = "";

        @BlankLineBefore
        @Comment("Debug")
        @Comment(lang = "zh", value = "调试选项.")
        DebugOptions debug = DebugOptions.DISABLE;
    }

    public record DebugOptions(
            boolean common
    ) {
        public static DebugOptions DISABLE = new DebugOptions(false);
    }

    public static boolean checkUpdate() {
        return config.updateChecker;
    }

    public static boolean metrics() {
        return config.metrics;
    }

    public static Locale forcedLocale() {
        return TranslationManager.parseLocale(config.forcedLocale);
    }
}

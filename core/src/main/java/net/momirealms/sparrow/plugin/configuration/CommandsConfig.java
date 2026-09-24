package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import net.momirealms.sparrow.yaml.upgrade.version.FieldVersionExtractor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class CommandsConfig {
    private static final String CONFIG_FILE = "commands.yml";

    private final Path configFilePath;
    private final YamlMapper<ConfigDefinition> configMapper;
    private final ConfigDefinition configDefinition;

    CommandsConfig(Path dataFolderPath, SparrowYaml sparrowYaml) {
        this.configFilePath = dataFolderPath.resolve(CONFIG_FILE);
        YamlUpgradePipeline upgradePipeline = YamlUpgradePipeline.builder()
                .versionExtractor(new FieldVersionExtractor("__version__"))
                .build();
        YamlMapperFactory mapperFactory = YamlMapperFactory.builder()
                .backupOnUpgrade(true)
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(upgradePipeline)
                .build();
        this.configMapper = mapperFactory.create(ConfigDefinition.class, ConfigDefinition::new);
        this.configDefinition = this.load();
    }

    @NotNull
    ConfigDefinition load() {
        try {
            return this.configMapper.load(this.configFilePath).value();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + CONFIG_FILE, e);
        }
    }

    public ConfigDefinition configDefinition() {
        return this.configDefinition;
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static class ConfigDefinition {
        @YamlProperty("__version__")
        @Comment("Do not modify this value")
        @Comment(lang = "zh", value = "配置版本, 请勿修改.")
        String configVersion = DependencyVersions.COMMANDS_CONFIG_VERSION;

        @BlankLineBefore
        @Comment({
                "",
                "For safety reasons, editing this file requires a restart to apply",
                ""
        })
        @Comment(lang = "zh", value = "修改命令开关、权限或用法后需要重启服务器.")
        CommandConfig reload = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " reload"),
                DependencyVersions.PROJECT_ID + ".command.admin.reload"
        );

        @BlankLineBefore
        CommandConfig featureEnable = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " feature-enable"),
                DependencyVersions.PROJECT_ID + ".command.admin.feature"
        );

        @BlankLineBefore
        CommandConfig featureDisable = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " feature-disable"),
                DependencyVersions.PROJECT_ID + ".command.admin.feature"
        );

        @BlankLineBefore
        CommandConfig featureList = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " feature-list"),
                DependencyVersions.PROJECT_ID + ".command.admin.feature"
        );

        /**
         * 返回指定内置 Feature 的命令配置.
         *
         * @param featureID Feature 标识
         * @return 对应的命令配置
         * @throws IllegalArgumentException 当标识不属于内置 Feature 时
         */
        @NotNull
        public CommandConfig command(@NotNull String featureID) {
            return switch (featureID) {
                case "reload" -> this.reload;
                case "feature_enable" -> this.featureEnable;
                case "feature_disable" -> this.featureDisable;
                case "feature_list" -> this.featureList;
                default -> throw new IllegalArgumentException("Unknown default command feature: " + featureID);
            };
        }
    }
}

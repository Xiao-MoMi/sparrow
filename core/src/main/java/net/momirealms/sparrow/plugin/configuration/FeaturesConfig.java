package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.feature.patrol.PatrolSettings;
import net.momirealms.sparrow.feature.server.ServerSettings;
import net.momirealms.sparrow.feature.quickshulker.QuickShulkerSettings;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.route.Route;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import net.momirealms.sparrow.yaml.upgrade.version.FieldVersionExtractor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class FeaturesConfig {
    private final Path path;
    private final SparrowYaml yaml;
    private final YamlMapper<ConfigDefinition> mapper;
    private volatile ConfigDefinition config;

    FeaturesConfig(@NotNull Path dataFolder, @NotNull SparrowYaml yaml) {
        this.path = dataFolder.resolve("features.yml");
        this.yaml = yaml;
        this.mapper = YamlMapperFactory.builder()
                .sparrowYaml(yaml)
                .backupOnUpgrade(true)
                .upgradePipeline(YamlUpgradePipeline.builder()
                        .versionExtractor(new FieldVersionExtractor("__version__"))
                        .build())
                .build()
                .create(ConfigDefinition.class, ConfigDefinition::new);
        this.config = this.load();
    }

    @NotNull
    public ConfigDefinition load() {
        try {
            return this.mapper.load(this.path).value();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load features.yml", exception);
        }
    }

    public void reload() {
        this.config = this.load();
    }

    /** 保存指定功能的开关, 保留文件中的其他选项和注释. */
    public void saveEnabled(@NotNull String id, boolean enabled) {
        FeatureSettings settings = this.config.settings(id);
        try {
            YamlDocument document = this.yaml.load(this.path);
            document.set(Route.from(id, "enabled"), enabled);
            document.save(this.path);
            settings.enabled(enabled);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save feature state: " + id, exception);
        }
    }

    @NotNull
    public ConfigDefinition config() {
        return this.config;
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class ConfigDefinition {
        @YamlProperty("__version__")
        @Comment("Configuration version. Do not modify.")
        @Comment(lang = "zh", value = "配置版本, 请勿修改.")
        private String version = DependencyVersions.FEATURES_CONFIG_VERSION;

        @BlankLineBefore
        @Comment("Right-click to open a shulker box held in your hand.")
        @Comment(lang = "zh", value = "手持潜影盒右键打开.")
        private QuickShulkerSettings quickShulker = new QuickShulkerSettings();

        @BlankLineBefore
        @Comment("Teleport to online players one by one with /patrol, starting from the player not patrolled for the longest time.")
        @Comment(lang = "zh", value = "使用 /patrol 轮流传送到在线玩家, 优先选择最久没被巡查的玩家.")
        private PatrolSettings patrol = new PatrolSettings();

        @BlankLineBefore
        @Comment("Send players to other backend servers with /server. Server names come from the proxy (BungeeCord/Velocity) config.")
        @Comment(lang = "zh", value = "使用 /server 把玩家送到其他后端服务器.")
        private ServerSettings server = new ServerSettings();

        @NotNull
        public QuickShulkerSettings quickShulker() {
            return this.quickShulker;
        }

        @NotNull
        public PatrolSettings patrol() {
            return this.patrol;
        }

        @NotNull
        public ServerSettings server() {
            return this.server;
        }

        /** 按功能 ID 获取对应的配置, 未知 ID 会抛出异常. */
        @NotNull
        public FeatureSettings settings(@NotNull String id) {
            return switch (id) {
                case "quick-shulker" -> this.quickShulker;
                case "patrol" -> this.patrol;
                case "server" -> this.server;
                default -> throw new IllegalArgumentException("Unknown feature: " + id);
            };
        }
    }
}

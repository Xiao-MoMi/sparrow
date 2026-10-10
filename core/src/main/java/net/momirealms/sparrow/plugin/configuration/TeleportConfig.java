package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.teleport.TeleportGroup;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;

public final class TeleportConfig {
    private static final String CONFIG_FILE = "teleport.yml";
    private static final String DEFAULT_GROUP = "default";
    private static volatile ConfigDefinition config; // 重载会换上完整的新快照, 读取可能发生在不同线程

    private final Path configFilePath;
    private final YamlMapper<ConfigDefinition> configMapper;

    TeleportConfig(@NotNull Path dataFolder, @NotNull SparrowYaml sparrowYaml) {
        this.configFilePath = dataFolder.resolve(CONFIG_FILE);
        this.configMapper = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
                .build()
                .create(ConfigDefinition.class, ConfigDefinition::new);
    }

    void reload() {
        try {
            config = this.configMapper.load(this.configFilePath).value();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + CONFIG_FILE, exception);
        }
    }

    // 名称留空或为 default 时取默认分组, 分组不存在时抛出 IllegalArgumentException
    @NotNull
    public static TeleportGroup group(@NotNull String name) {
        ConfigDefinition current = config;
        if (name.isEmpty() || name.equals(DEFAULT_GROUP)) return current.defaultGroup;
        TeleportGroup group = current.groups.get(name);
        if (group == null) throw new IllegalArgumentException("Unknown teleport group: " + name);
        return group;
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class ConfigDefinition {
        @YamlProperty("__version__")
        @Comment("Do not modify this value")
        @Comment(lang = "zh", value = "配置版本, 请勿修改.")
        String configVersion = DependencyVersions.TELEPORT_CONFIG_VERSION;

        @BlankLineBefore
        @Comment({
                "The default teleport group. A feature uses it unless it names another group with teleport-group in features.yml.",
                "A teleport passes through the processors of its group in the listed order. Each processor either lets it continue or rejects it.",
                "Remove a processor from the list to turn it off. Its options below are then ignored.",
                "Processors registered by other plugins are used the same way, by adding their ID to the list.",
                "Teleports started on behalf of another player, such as /warp <name> <player>, skip the cooldown and the warmup."
        })
        @Comment(lang = "zh", value = {
                "默认传送分组. 功能没有在 features.yml 中用 teleport-group 指定其他分组时使用它.",
                "传送会按列表顺序依次经过分组中的处理器, 每个处理器可以放行或拒绝这次传送.",
                "从列表中删掉某个处理器即可关闭它, 下方对应的选项随之失效.",
                "其他插件注册的处理器用法相同, 把它的 ID 写进列表即可.",
                "代其他玩家发起的传送 (例如 /warp <名称> <玩家>) 不经过冷却和预热."
        })
        @YamlProperty("default")
        TeleportGroup defaultGroup = new TeleportGroup();

        @BlankLineBefore
        @Comment({
                "Custom groups, written the same way as \"default\". Options left out take the built-in defaults, not the values of \"default\".",
                "Example, a group without cooldown or warmup:",
                "groups:",
                "   instant:",
                "     processors: []"
        })
        @Comment(lang = "zh", value = {
                "自定义分组, 写法与 default 相同. 没有写出的选项使用内置默认值, 不继承 default 中的值.",
                "示例, 一个没有冷却和预热的分组:",
                "groups:",
                "   instant:",
                "     processors: []"
        })
        Map<String, TeleportGroup> groups = Map.of();
    }
}

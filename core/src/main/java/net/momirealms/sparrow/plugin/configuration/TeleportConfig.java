package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.configuration.serializer.TeleportProcessorSerializer;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.teleport.TeleportGroup;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TeleportConfig {
    private static final String CONFIG_FILE = "teleport.yml";
    private static final String DEFAULT_GROUP = "default";
    private static volatile ConfigDefinition config; // 重载会换上完整的新快照, 读取可能发生在不同线程

    private final Path configFilePath;
    private final TeleportProcessorSerializer processorSerializer;
    private final YamlMapper<ConfigDefinition> configMapper;

    TeleportConfig(@NotNull Path dataFolder, @NotNull SparrowYaml sparrowYaml) {
        this.configFilePath = dataFolder.resolve(CONFIG_FILE);
        this.processorSerializer = new TeleportProcessorSerializer(sparrowYaml);
        sparrowYaml.serializers().register(new TypeRef<List<TeleportProcessor.Pre>>() {}, this.processorSerializer.serializer(TeleportProcessor.Pre.class));
        sparrowYaml.serializers().register(new TypeRef<List<TeleportProcessor.Target>>() {}, this.processorSerializer.serializer(TeleportProcessor.Target.class));
        sparrowYaml.serializers().register(new TypeRef<List<TeleportProcessor.Post>>() {}, this.processorSerializer.serializer(TeleportProcessor.Post.class));
        this.configMapper = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
                .build()
                .create(ConfigDefinition.class, ConfigDefinition::new);
    }

    // 返回这次加载中被忽略的处理器数量
    int reload() {
        try {
            config = this.configMapper.load(this.configFilePath).value();
            return this.processorSerializer.takeIssues();
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
                "A group lists the processors a teleport goes through at each stage. It only runs what it lists, so removing an entry turns that processor off.",
                "An entry that is written wrong is ignored and reported in the console.",
                "Teleports started on behalf of another player, such as /warp <name> <player>, skip the cooldown, the warmup and the world blacklist."
        })
        @Comment(lang = "zh", value = {
                "默认传送分组. 功能没有在 features.yml 中用 teleport-group 指定其他分组时使用它.",
                "分组列出一次传送在各个阶段要经过的处理器. 分组只执行列出的处理器, 删掉某一项即可关闭它.",
                "写错的项会被忽略, 并在控制台给出警告.",
                "代其他玩家发起的传送 (例如 /warp <名称> <玩家>) 不经过冷却、预热和世界黑名单."
        })
        @YamlProperty("default")
        TeleportGroup defaultGroup = TeleportGroup.createDefault();

        @BlankLineBefore
        @Comment({
                "Custom groups, written the same way as \"default\". A stage left out of a group runs nothing.",
                "Example, a group that teleports at once and only plays the arrival sound:",
                "groups:",
                "   instant:",
                "     post-processor:",
                "       - processor-type: sound"
        })
        @Comment(lang = "zh", value = {
                "自定义分组, 写法与 default 相同. 分组里没有写出的阶段不执行任何处理器.",
                "示例, 一个立即传送、只播放到达音效的分组:",
                "groups:",
                "   instant:",
                "     post-processor:",
                "       - processor-type: sound"
        })
        Map<String, TeleportGroup> groups = Map.of();
    }
}
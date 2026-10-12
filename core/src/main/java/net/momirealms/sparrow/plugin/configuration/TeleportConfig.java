package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.serializer.TeleportProcessorSerializer;
import net.momirealms.sparrow.plugin.configuration.serializer.TeleportSearchSerializer;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.teleport.TeleportGroup;
import net.momirealms.sparrow.teleport.processor.TeleportProcessor;
import net.momirealms.sparrow.teleport.search.TeleportSearchOptions;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.TypeRef;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TeleportConfig {
    private static final String CONFIG_FILE = "teleport.yml";
    private static final String DEFAULT_GROUP = "default";
    private static volatile Snapshot config; // 重载会换上完整的新快照, 读取可能发生在不同线程

    private final Path configFilePath;
    private final TeleportProcessorSerializer processorSerializer;
    private final SparrowYaml sparrowYaml;
    private final NodeSerializer<TeleportGroup> groupSerializer;
    private final YamlMapper<ConfigDefinition> configMapper;

    TeleportConfig(@NotNull Path dataFolder, @NotNull SparrowYaml sparrowYaml) {
        this.configFilePath = dataFolder.resolve(CONFIG_FILE);
        this.sparrowYaml = sparrowYaml;
        this.processorSerializer = new TeleportProcessorSerializer(sparrowYaml);
        sparrowYaml.serializers().register(new TypeRef<List<TeleportProcessor.Pre>>() {}, this.processorSerializer.serializer(TeleportProcessor.Pre.class));
        sparrowYaml.serializers().register(new TypeRef<List<TeleportProcessor.RequestRule>>() {}, this.processorSerializer.serializer(TeleportProcessor.RequestRule.class));
        sparrowYaml.serializers().register(new TypeRef<List<TeleportProcessor.CandidateRule>>() {}, this.processorSerializer.serializer(TeleportProcessor.CandidateRule.class));
        sparrowYaml.serializers().register(new TypeRef<List<TeleportProcessor.Post>>() {}, this.processorSerializer.serializer(TeleportProcessor.Post.class));
        sparrowYaml.serializers().register(TeleportSearchOptions.class, new TeleportSearchSerializer(sparrowYaml).serializer());
        this.groupSerializer = sparrowYaml.serializers().register(TeleportGroup.class);
        this.configMapper = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
                .build()
                .create(ConfigDefinition.class, this::createDefaults);
    }

    private ConfigDefinition createDefaults() {
        ConfigDefinition defaults = new ConfigDefinition();
        defaults.groups.put("death-back", this.withoutNullOptions(this.groupSerializer.serialize(TeleportGroup.createDeathBack())));
        defaults.groups.put("instant", this.withoutNullOptions(this.groupSerializer.serialize(TeleportGroup.createInstant())));
        defaults.groups.put("rtp", this.withoutNullOptions(this.groupSerializer.serialize(TeleportGroup.createRtp())));
        return defaults;
    }

    // 默认分组树还要经过原始节点解码, 序列化产生的空选项按缺省处理
    private Object withoutNullOptions(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> options = new LinkedHashMap<>();
            map.forEach((key, option) -> {
                if (option != null) options.put(key, this.withoutNullOptions(option));
            });
            return options;
        }
        if (value instanceof List<?> list) return list.stream().map(this::withoutNullOptions).toList();
        return value;
    }

    // 默认组错误直接抛出, 命名组分别解析并回退, 返回回退的组数
    int reload() {
        try {
            ConfigDefinition loaded = this.configMapper.load(this.configFilePath).value();
            loaded.defaultGroup.validate();
            Map<String, TeleportGroup> groups = new LinkedHashMap<>();
            int issues = 0;
            for (Map.Entry<String, Object> entry : loaded.groups.entrySet()) {
                try {
                    YamlDocument document = this.sparrowYaml.createDocument();
                    ((Map<?, ?>) entry.getValue()).forEach(document::setSubNode);
                    TeleportGroup group = this.groupSerializer.deserialize(document);
                    group.validate();
                    groups.put(entry.getKey(), group);
                } catch (RuntimeException error) {
                    SparrowPlugin.instance().logger().warn("Invalid teleport group '" + entry.getKey() + "', using the default group: " + error.getMessage());
                    groups.put(entry.getKey(), loaded.defaultGroup);
                    issues++;
                }
            }
            config = new Snapshot(loaded.defaultGroup, Map.copyOf(groups));
            return issues;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + CONFIG_FILE, exception);
        }
    }

    public void registerProcessor(@NotNull SparrowKey type, @NotNull Class<? extends TeleportProcessor> processorType) {
        this.processorSerializer.register(type, processorType);
    }

    // 名称留空或为 default 时取默认分组, 分组不存在时抛出 IllegalArgumentException
    @NotNull
    public static TeleportGroup group(@NotNull String name) {
        Snapshot current = config;
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
                "Invalid default configuration rejects loading. Invalid named groups are logged and use the default group.",
                "Processors registered by other plugins are used the same way, with the type they registered, such as myplugin:example.",
                "Teleports started on behalf of another player, such as /warp <name> <player>, skip the cooldown, the warmup and the world blacklist."
        })
        @Comment(lang = "zh", value = {
                "默认传送分组. 功能没有在 features.yml 中用 teleport-group 指定其他分组时使用它.",
                "分组列出一次传送在各个阶段要经过的处理器. 分组只执行列出的处理器, 删掉某一项即可关闭它.",
                "默认组配置错误时拒绝加载. 命名组错误会记录日志, 运行时使用本次加载的默认组.",
                "其他插件注册的处理器用法相同, processor-type 填它注册的类型, 例如 myplugin:example.",
                "代其他玩家发起的传送 (例如 /warp <名称> <玩家>) 不经过冷却、预热和世界黑名单."
        })
        @YamlProperty("default")
        TeleportGroup defaultGroup = TeleportGroup.createDefault();

        @BlankLineBefore
        @Comment({
                "Named groups, written the same way as \"default\". A stage left out of a group runs nothing.",
                "death-back searches for a safe landing spot for /death-back and grants 5 seconds of invulnerability after arrival.",
                "instant teleports at once and only plays the arrival sound. Assign it to a feature to use it."
        })
        @Comment(lang = "zh", value = {
                "命名分组, 写法与 default 相同. 分组里没有写出的阶段不执行任何处理器.",
                "death-back 为 /death-back 搜索安全落点, 到达后给予 5 秒无敌.",
                "instant 立即传送并播放到达音效, 将功能的传送分组设为 instant 即可使用."
        })
        Map<String, Object> groups = new LinkedHashMap<>();
    }

    private record Snapshot(TeleportGroup defaultGroup, Map<String, TeleportGroup> groups) {
    }
}
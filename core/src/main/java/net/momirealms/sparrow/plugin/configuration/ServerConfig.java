package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import net.momirealms.sparrow.yaml.upgrade.YamlUpgradePipeline;
import net.momirealms.sparrow.yaml.upgrade.version.FieldVersionExtractor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class ServerConfig {
    private static final String CONFIG_FILE = "server.yml";
    private static volatile ConfigDefinition config;

    private final Path configFilePath;
    private final YamlMapper<ConfigDefinition> configMapper;
    private String startupServerId;

    ServerConfig(@NotNull Path dataFolder, @NotNull SparrowYaml sparrowYaml) {
        this.configFilePath = dataFolder.resolve(CONFIG_FILE);
        this.configMapper = YamlMapperFactory.builder()
                .backupOnUpgrade(true)
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(YamlUpgradePipeline.builder()
                        .versionExtractor(new FieldVersionExtractor("__version__"))
                        .build())
                .build()
                .create(ConfigDefinition.class, ConfigDefinition::new);
    }

    void reload() {
        try {
            ConfigDefinition loaded = this.configMapper.load(this.configFilePath).value();
            // 心跳和消息代理在启动时占用了身份, 运行期间沿用启动时的 server-id
            if (this.startupServerId != null) {
                loaded.serverId = this.startupServerId;
            } else {
                this.startupServerId = loaded.serverId;
            }
            config = loaded;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + CONFIG_FILE, exception);
        }
    }

    /**
     * 返回本服在集群中的唯一标识, 用于 Redis 心跳、跨服消息、在线名单和按服务器归属的数据. 未配置时为空串.
     */
    @NotNull
    public static String serverId() {
        return config.serverId;
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class ConfigDefinition {
        @YamlProperty("__version__")
        @Comment("Do not modify this value")
        @Comment(lang = "zh", value = "配置版本, 请勿修改.")
        String configVersion = DependencyVersions.SERVER_CONFIG_VERSION;

        @Comment({
                "A unique ID for this server. It should match the ID in the proxy configuration. Every server sharing the same Redis and database must use a different value.",
                "Set this before startup. Leaving it blank shuts down the server.",
                "Avoid changing it later. Data such as warps records the server it belongs to by this ID."
        })
        @Comment(lang = "zh", value = {
                "本服的唯一 ID, 应该与 Proxy 配置中的 ID 一致, 共用同一 Redis 和数据库的每台服务器都要填不同的值.",
                "请在启动前填写, 留空会导致服务器关闭.",
                "设置后尽量不要修改, 地标等数据会用它记录所属服务器."
        })
        String serverId = "";
    }
}

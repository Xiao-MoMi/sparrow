package net.momirealms.sparrow.proxy.common.config;

import net.momirealms.sparrow.proxy.common.BuildInfo;
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

// 代理端配置文件
@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public class ProxyConfig {
    private static final String CONFIG_FILE = "config.yml";

    @YamlProperty("__version__")
    @Comment("Do not modify this value")
    @Comment(lang = "zh", value = "配置版本, 请勿修改.")
    String configVersion = BuildInfo.CONFIG_VERSION;

    @Comment("Redis connection settings. Use the same Redis as the backend servers. Changes take effect after a proxy restart.")
    @Comment(lang = "zh", value = "Redis 连接设置, 需要和后端服务器使用同一个 Redis, 修改后需要重启代理.")
    RedisOptions redis = new RedisOptions();

    /**
     * 读取数据目录下的配置文件, 文件不存在时写入默认配置.
     */
    @NotNull
    public static ProxyConfig load(@NotNull Path dataFolder) {
        SparrowYaml sparrowYaml = SparrowYaml.builder()
                .setAllowDuplicateKeys(false)
                .setAllowObjectKeys(false)
                .build();
        YamlUpgradePipeline upgradePipeline = YamlUpgradePipeline.builder()
                .versionExtractor(new FieldVersionExtractor("__version__"))
                .build();
        YamlMapper<ProxyConfig> mapper = YamlMapperFactory.builder()
                .backupOnUpgrade(true)
                .sparrowYaml(sparrowYaml)
                .upgradePipeline(upgradePipeline)
                .build()
                .create(ProxyConfig.class, ProxyConfig::new);
        try {
            return mapper.loadValue(dataFolder.resolve(CONFIG_FILE));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + CONFIG_FILE, e);
        }
    }

    @NotNull
    public RedisOptions redis() {
        return this.redis;
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class RedisOptions {
        String url = "redis://localhost:6379/0";
        String username = "";
        String password = "";

        @NotNull
        public String url() {
            return this.url;
        }

        @NotNull
        public String username() {
            return this.username;
        }

        @NotNull
        public String password() {
            return this.password;
        }
    }
}

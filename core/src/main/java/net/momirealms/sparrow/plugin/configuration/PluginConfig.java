package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.database.DatabaseType;
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
    private DatabaseOptions startupDatabase;
    private RedisOptions startupRedis;

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
            ConfigDefinition loaded = this.configMapper.load(this.configFilePath).value();
            if (this.startupDatabase != null) {
                loaded.database = this.startupDatabase;
                loaded.redis = this.startupRedis;
            } else {
                this.startupDatabase = loaded.database;
                this.startupRedis = loaded.redis;
            }
            config = loaded;
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
        @Comment("Redis connection settings. Changes take effect after a server restart.")
        @Comment(lang = "zh", value = "Redis 连接设置, 修改后需要重启服务器.")
        RedisOptions redis = new RedisOptions();

        @BlankLineBefore
        @Comment("Database connection settings. Changes take effect after a server restart.")
        @Comment(lang = "zh", value = "数据库连接设置, 修改后需要重启服务器.")
        DatabaseOptions database = new DatabaseOptions();
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class RedisOptions {
        String url = "redis://localhost:6379/0";
        String username = "";
        String password = "";

        public String url() {
            return this.url;
        }

        public String username() {
            return this.username;
        }

        public String password() {
            return this.password;
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class DatabaseOptions {
        @Comment("Select MONGODB, MYSQL, MARIADB or POSTGRESQL.")
        @Comment(lang = "zh", value = "可选 MONGODB、MYSQL、MARIADB 或 POSTGRESQL.")
        DatabaseType type = DatabaseType.MONGODB;

        MysqlOptions mysql = new MysqlOptions();
        MariaDbOptions mariadb = new MariaDbOptions();
        PostgresOptions postgresql = new PostgresOptions();
        MongoOptions mongodb = new MongoOptions();

        public DatabaseType type() {
            return this.type;
        }

        public MysqlOptions mysql() {
            return this.mysql;
        }

        public MariaDbOptions mariadb() {
            return this.mariadb;
        }

        public PostgresOptions postgresql() {
            return this.postgresql;
        }

        public MongoOptions mongodb() {
            return this.mongodb;
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class SqlOptions {
        String url;
        String username;
        String password = "";
        String tablePrefix = "sparrow_";

        public String url() {
            return this.url;
        }

        public String username() {
            return this.username;
        }

        public String password() {
            return this.password;
        }

        public String tablePrefix() {
            return this.tablePrefix;
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class MysqlOptions extends SqlOptions {
        public MysqlOptions() {
            this.url = "jdbc:mysql://localhost:3306/minecraft?connectTimeout=5000&socketTimeout=10000&characterEncoding=UTF-8";
            this.username = "root";
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class MariaDbOptions extends SqlOptions {
        public MariaDbOptions() {
            this.url = "jdbc:mariadb://localhost:3306/minecraft?connectTimeout=5000&socketTimeout=10000&characterEncoding=UTF-8";
            this.username = "root";
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class PostgresOptions extends SqlOptions {
        public PostgresOptions() {
            this.url = "jdbc:postgresql://localhost:5432/minecraft?connectTimeout=5&socketTimeout=10";
            this.username = "postgres";
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class MongoOptions {
        String url = "mongodb://localhost:27017";
        String database = "minecraft";
        String username = "";
        String password = "";
        String authSource = "admin";
        String collectionPrefix = "sparrow_";

        public String url() {
            return this.url;
        }

        public String database() {
            return this.database;
        }

        public String username() {
            return this.username;
        }

        public String password() {
            return this.password;
        }

        public String authSource() {
            return this.authSource;
        }

        public String collectionPrefix() {
            return this.collectionPrefix;
        }
    }

    public static RedisOptions redis() {
        return config.redis;
    }

    public static DatabaseOptions database() {
        return config.database;
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

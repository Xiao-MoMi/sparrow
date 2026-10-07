package net.momirealms.sparrow.plugin.configuration;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minecraft.world.BossEvent;
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
import net.momirealms.sparrow.yaml.serializer.auto.annotation.YamlProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        YamlMapperFactory mapperFactory = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
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
        @YamlProperty("__version__")
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

        @BlankLineBefore
        @Comment("Default text parsing options for actionbar, broadcast, title and toast commands.")
        @Comment(lang = "zh", value = "ActionBar、广播、标题和进度提示命令的默认文本解析选项.")
        TextOptions textOptions = new TextOptions();

        @BlankLineBefore
        @Comment("How teleport warmups look and sound. Warmup and cooldown seconds are set by each feature.")
        @Comment(lang = "zh", value = "传送预热的显示与音效. 预热和冷却秒数由各功能自己设置.")
        TeleportDisplay teleport = new TeleportDisplay();
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

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class TextOptions {
        @Comment("Parse PlaceholderAPI by default. --parse/-p also enables parsing for an individual command.")
        @Comment(lang = "zh", value = "默认解析 PlaceholderAPI 占位符. 命令中的 --parse/-p 也可单独启用解析.")
        boolean parsePlaceholder = true;

        @Comment("Parse legacy color codes by default. --legacy-color/-l also enables parsing for an individual command.")
        @Comment(lang = "zh", value = "默认解析传统颜色代码. 命令中的 --legacy-color/-l 也可单独启用解析.")
        boolean parseLegacyColor = false;

        public boolean parsePlaceholder() {
            return this.parsePlaceholder;
        }

        public boolean parseLegacyColor() {
            return this.parseLegacyColor;
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class TeleportDisplay {
        @Comment("Where the warmup countdown is shown: ACTION_BAR, TITLE, BOSS_BAR, CHAT or NONE.")
        @Comment(lang = "zh", value = "预热倒计时显示的位置: ACTION_BAR (动作栏)、TITLE (屏幕中央)、BOSS_BAR (进度条)、CHAT (聊天栏) 或 NONE (不显示).")
        WarmupDisplay warmupDisplay = WarmupDisplay.ACTION_BAR;

        @Comment("Boss bar color when warmup-display is BOSS_BAR: PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE.")
        @Comment(lang = "zh", value = "warmup-display 为 BOSS_BAR 时的颜色: PINK、BLUE、RED、GREEN、YELLOW、PURPLE 或 WHITE.")
        BossEvent.BossBarColor bossBarColor = BossEvent.BossBarColor.YELLOW;

        @Comment("Boss bar style: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12 or NOTCHED_20.")
        @Comment(lang = "zh", value = "BossBar 样式: PROGRESS、NOTCHED_6、NOTCHED_10、NOTCHED_12 或 NOTCHED_20.")
        BossEvent.BossBarOverlay bossBarOverlay = BossEvent.BossBarOverlay.PROGRESS;

        @Comment("Sound keys, such as entity.enderman.teleport. Leave empty to play nothing.")
        @Comment(lang = "zh", value = "音效名称, 例如 entity.enderman.teleport. 留空表示不播放.")
        String warmupSound = "block.note_block.banjo";
        String completeSound = "entity.enderman.teleport";
        String cancelSound = "entity.item.break";

        public WarmupDisplay warmupDisplay() {
            return this.warmupDisplay;
        }

        @NotNull
        public BossEvent.BossBarColor bossBarColor() {
            return this.bossBarColor;
        }

        @NotNull
        public BossEvent.BossBarOverlay bossBarOverlay() {
            return this.bossBarOverlay;
        }

        @Nullable
        public Sound warmupSound() {
            return sound(this.warmupSound);
        }

        @Nullable
        public Sound completeSound() {
            return sound(this.completeSound);
        }

        @Nullable
        public Sound cancelSound() {
            return sound(this.cancelSound);
        }

        // 留空或不是合法的键时不播放
        @Nullable
        private static Sound sound(String key) {
            if (key.isEmpty() || !Key.parseable(key)) return null;
            return Sound.sound(Key.key(key), Sound.Source.MASTER, 1.0f, 1.0f);
        }
    }

    public enum WarmupDisplay {
        ACTION_BAR,
        TITLE,
        BOSS_BAR,
        CHAT,
        NONE
    }

    public static TextOptions text() {
        return config.textOptions;
    }

    public static TeleportDisplay teleport() {
        return config.teleport;
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
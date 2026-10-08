package net.momirealms.sparrow.plugin.configuration;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.feature.back.BackSettings;
import net.momirealms.sparrow.feature.ban.BanSettings;
import net.momirealms.sparrow.feature.bed.BedSettings;
import net.momirealms.sparrow.feature.head.HeadSettings;
import net.momirealms.sparrow.feature.home.HomeSettings;
import net.momirealms.sparrow.feature.highlight.HighlightSettings;
import net.momirealms.sparrow.feature.maintenance.MaintenanceSettings;
import net.momirealms.sparrow.feature.playerlimit.PlayerLimitSettings;
import net.momirealms.sparrow.feature.patrol.PatrolSettings;
import net.momirealms.sparrow.feature.server.ServerSettings;
import net.momirealms.sparrow.feature.spawn.SpawnSettings;
import net.momirealms.sparrow.feature.quickshulker.QuickShulkerSettings;
import net.momirealms.sparrow.feature.warp.WarpSettings;
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
                .build()
                .create(ConfigDefinition.class, ConfigDefinition::new);
        this.config = this.load();
    }

    @NotNull
    private ConfigDefinition load() {
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

    /** 保存维护状态, 保留文件中的其他选项和注释. */
    public void saveMaintenanceActive(boolean active) {
        MaintenanceSettings settings = this.config.maintenance();
        try {
            YamlDocument document = this.yaml.load(this.path);
            document.set(Route.from("maintenance", "active"), active);
            document.save(this.path);
            settings.active(active);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save maintenance state", exception);
        }
    }

    /** 保存人数上限, 保留文件中的其他选项和注释. */
    public void saveMaxPlayers(int maxPlayers) {
        PlayerLimitSettings settings = this.config.playerLimit();
        try {
            YamlDocument document = this.yaml.load(this.path);
            document.set(Route.from("player-limit", "max-players"), maxPlayers);
            document.save(this.path);
            settings.maxPlayers(maxPlayers);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save player limit", exception);
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

        @BlankLineBefore
        @Comment("Select a region and show glowing block outlines to players.")
        @Comment(lang = "zh", value = "选择区域并向玩家显示方块发光轮廓.")
        private HighlightSettings highlight = new HighlightSettings();

        @BlankLineBefore
        @Comment("Fetch player heads and URL textures, with memory and Redis caches.")
        @Comment(lang = "zh", value = "获取玩家头颅和 URL 纹理, 使用内存及 Redis 缓存.")
        private HeadSettings head = new HeadSettings();

        @BlankLineBefore
        @Comment("Maintenance mode. Only players with the bypass permission can stay or join while it is active.")
        @Comment(lang = "zh", value = "维护模式, 开启后只有拥有绕过权限的玩家可以留在或进入服务器.")
        private MaintenanceSettings maintenance = new MaintenanceSettings();

        @BlankLineBefore
        @Comment("Change the player limit at runtime with /max-players. Players with the bypass permission can join a full server.")
        @Comment(lang = "zh", value = "使用 /max-players 动态修改人数上限, 拥有绕过权限的玩家可以在满员时进入.")
        private PlayerLimitSettings playerLimit = new PlayerLimitSettings();

        @BlankLineBefore
        @Comment("Network-wide bans by account, IP or IP wildcard, checked on login.")
        @Comment(lang = "zh", value = "按账号、IP 或通配 IP 全服封禁, 在登录时检查.")
        private BanSettings ban = new BanSettings();

        @BlankLineBefore
        @Comment("Return to the previous teleport location or server with /back, and to the last death location with /death-back.")
        @Comment(lang = "zh", value = "使用 /back 返回上次传送前的位置或上一个服务器, 使用 /death-back 返回上次死亡位置.")
        private BackSettings back = new BackSettings();

        @BlankLineBefore
        @Comment("Return to beds with /bed, with configurable warmup and cooldown. Teleporting other players is immediate and does not use cooldowns.")
        @Comment(lang = "zh", value = "使用 /bed 回到床边, 可设置预热与冷却. 传送其他玩家时立即执行, 不检查或记录冷却.")
        private BedSettings bed = new BedSettings();

        @BlankLineBefore
        @Comment("Return to the network-wide spawn with /spawn. Set it at the current location with /set-spawn, or clear it with /del-spawn.")
        @Comment(lang = "zh", value = "使用 /spawn 返回全服共用的 Spawn. /set-spawn 设置为当前位置, /del-spawn 清除.")
        private SpawnSettings spawn = new SpawnSettings();

        @BlankLineBefore
        @Comment("Warps shared by every server, used with /warp, /set-warp, /del-warp and /warp-list.")
        @Comment(lang = "zh", value = "所有服务器共用的 warp, 使用 /warp、/set-warp、/del-warp 和 /warp-list.")
        private WarpSettings warp = new WarpSettings();

        @BlankLineBefore
        @Comment("Personal homes, with local caching and cross-server change messages.")
        @Comment(lang = "zh", value = "个人 Home, 使用本地缓存与跨服变更消息同步.")
        private HomeSettings home = new HomeSettings();

        @NotNull
        public HomeSettings home() {
            return this.home;
        }

        @NotNull
        public BanSettings ban() {
            return this.ban;
        }

        @NotNull
        public BackSettings back() {
            return this.back;
        }

        @NotNull
        public BedSettings bed() {
            return this.bed;
        }

        @NotNull
        public SpawnSettings spawn() {
            return this.spawn;
        }

        @NotNull
        public WarpSettings warp() {
            return this.warp;
        }

        @NotNull
        public MaintenanceSettings maintenance() {
            return this.maintenance;
        }

        @NotNull
        public PlayerLimitSettings playerLimit() {
            return this.playerLimit;
        }

        @NotNull
        public HeadSettings head() {
            return this.head;
        }

        @NotNull
        public HighlightSettings highlight() {
            return this.highlight;
        }

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
                case "highlight" -> this.highlight;
                case "head" -> this.head;
                case "maintenance" -> this.maintenance;
                case "player-limit" -> this.playerLimit;
                case "ban" -> this.ban;
                case "back" -> this.back;
                case "bed" -> this.bed;
                case "spawn" -> this.spawn;
                case "warp" -> this.warp;
                case "home" -> this.home;
                default -> throw new IllegalArgumentException("Unknown feature: " + id);
            };
        }
    }
}

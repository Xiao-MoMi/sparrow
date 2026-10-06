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

        // base: 插件自身的管理命令
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

        // a-z: 不属于任何模块的独立命令, 插件启用时注册
        @BlankLineBefore
        CommandConfig actionbar = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " actionbar", "/actionbar"), DependencyVersions.PROJECT_ID + ".command.actionbar");

        @BlankLineBefore
        CommandConfig anvil = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " anvil", "/anvil"), DependencyVersions.PROJECT_ID + ".command.anvil");

        @BlankLineBefore
        CommandConfig broadcast = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " broadcast", "/broadcast"), DependencyVersions.PROJECT_ID + ".command.broadcast");

        @BlankLineBefore
        CommandConfig burn = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " burn", "/burn"), DependencyVersions.PROJECT_ID + ".command.burn");

        @BlankLineBefore
        @YamlProperty("cartography-table")
        CommandConfig cartographyTable = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " cartography-table", "/cartography-table"), DependencyVersions.PROJECT_ID + ".command.cartography-table");

        @BlankLineBefore
        CommandConfig color = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " color", "/color"), DependencyVersions.PROJECT_ID + ".command.color");

        @BlankLineBefore
        CommandConfig credits = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " credits", "/credits"), DependencyVersions.PROJECT_ID + ".command.credits");

        @BlankLineBefore
        @YamlProperty("custom-model-data")
        CommandConfig customModelData = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " custom_model_data", "/custom_model_data"), DependencyVersions.PROJECT_ID + ".command.custom-model-data");

        @BlankLineBefore
        @YamlProperty("custom-name")
        CommandConfig customName = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " custom_name", "/custom_name"), DependencyVersions.PROJECT_ID + ".command.custom-name");

        @BlankLineBefore
        CommandConfig demo = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " demo", "/demo"), DependencyVersions.PROJECT_ID + ".command.demo");

        @BlankLineBefore
        CommandConfig distance = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " distance", "/distance"), DependencyVersions.PROJECT_ID + ".command.distance");

        @BlankLineBefore
        CommandConfig enchant = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " enchant", "/enchant"), DependencyVersions.PROJECT_ID + ".command.enchant");

        @BlankLineBefore
        @YamlProperty("enchantment-table")
        CommandConfig enchantmentTable = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " enchantment-table", "/enchantment-table"), DependencyVersions.PROJECT_ID + ".command.enchantment-table");

        @BlankLineBefore
        @YamlProperty("ender-chest")
        CommandConfig enderChest = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " ender_chest", "/ender_chest"), DependencyVersions.PROJECT_ID + ".command.ender-chest");

        @BlankLineBefore
        CommandConfig extinguish = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " extinguish", "/extinguish"), DependencyVersions.PROJECT_ID + ".command.extinguish");

        @BlankLineBefore
        CommandConfig feed = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " feed", "/feed"), DependencyVersions.PROJECT_ID + ".command.feed");

        @BlankLineBefore
        CommandConfig fly = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " fly", "/fly"), DependencyVersions.PROJECT_ID + ".command.fly");

        @BlankLineBefore
        @YamlProperty("fly-speed")
        CommandConfig flySpeed = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " fly-speed", "/fly-speed"), DependencyVersions.PROJECT_ID + ".command.fly-speed");

        @BlankLineBefore
        CommandConfig grindstone = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " grindstone", "/grindstone"), DependencyVersions.PROJECT_ID + ".command.grindstone");

        @BlankLineBefore
        CommandConfig hat = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " hat", "/hat"), DependencyVersions.PROJECT_ID + ".command.hat");

        @BlankLineBefore
        CommandConfig heal = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " heal", "/heal"), DependencyVersions.PROJECT_ID + ".command.heal");

        @BlankLineBefore
        CommandConfig ip = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " ip", "/ip"), DependencyVersions.PROJECT_ID + ".command.ip");

        @BlankLineBefore
        @YamlProperty("ip-history")
        CommandConfig ipHistory = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " ip-history", "/ip-history"), DependencyVersions.PROJECT_ID + ".command.ip-history");

        @BlankLineBefore
        @YamlProperty("item-data")
        CommandConfig itemData = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " item_data", "/item_data"), DependencyVersions.PROJECT_ID + ".command.item-data");

        @BlankLineBefore
        @YamlProperty("item-lore")
        CommandConfig itemLore = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " item_lore", "/item_lore"), DependencyVersions.PROJECT_ID + ".command.item-lore");

        @BlankLineBefore
        @YamlProperty("item-name")
        CommandConfig itemName = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " item_name", "/item_name"), DependencyVersions.PROJECT_ID + ".command.item-name");

        @BlankLineBefore
        CommandConfig kick = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " kick", "/kick"), DependencyVersions.PROJECT_ID + ".command.kick");

        @BlankLineBefore
        CommandConfig knockback = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " knockback", "/knockback"), DependencyVersions.PROJECT_ID + ".command.knockback");

        @BlankLineBefore
        CommandConfig look = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " look", "/look"), DependencyVersions.PROJECT_ID + ".command.look");

        @BlankLineBefore
        CommandConfig more = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " more", "/more"), DependencyVersions.PROJECT_ID + ".command.more");

        @BlankLineBefore
        @YamlProperty("player-name")
        CommandConfig playerName = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " player-name", "/player-name"), DependencyVersions.PROJECT_ID + ".command.player-name");

        @BlankLineBefore
        @YamlProperty("player-uuid")
        CommandConfig playerUuid = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " player-uuid", "/player-uuid"), DependencyVersions.PROJECT_ID + ".command.player-uuid");

        @BlankLineBefore
        @YamlProperty("smithing-table")
        CommandConfig smithingTable = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " smithing-table", "/smithing-table"), DependencyVersions.PROJECT_ID + ".command.smithing-table");

        @BlankLineBefore
        CommandConfig stonecutter = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " stonecutter", "/stonecutter"), DependencyVersions.PROJECT_ID + ".command.stonecutter");

        @BlankLineBefore
        CommandConfig sudo = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " sudo", "/sudo"), DependencyVersions.PROJECT_ID + ".command.sudo");

        @BlankLineBefore
        CommandConfig suicide = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " suicide", "/suicide"), DependencyVersions.PROJECT_ID + ".command.suicide");

        @BlankLineBefore
        CommandConfig title = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " title", "/title"), DependencyVersions.PROJECT_ID + ".command.title");

        @BlankLineBefore
        CommandConfig toast = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " toast", "/toast"), DependencyVersions.PROJECT_ID + ".command.toast");

        @BlankLineBefore
        @YamlProperty("top-block")
        CommandConfig topBlock = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " top-block", "/top-block"), DependencyVersions.PROJECT_ID + ".command.top-block");

        @BlankLineBefore
        @YamlProperty("totem-animation")
        CommandConfig totemAnimation = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " totem-animation", "/totem-animation"), DependencyVersions.PROJECT_ID + ".command.totem-animation");

        @BlankLineBefore
        @YamlProperty("tp-offline")
        CommandConfig tpOffline = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " tp-offline", "/tp-offline"), DependencyVersions.PROJECT_ID + ".command.tp-offline");

        @BlankLineBefore
        @YamlProperty("walk-speed")
        CommandConfig walkSpeed = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " walk-speed", "/walk-speed"), DependencyVersions.PROJECT_ID + ".command.walk-speed");

        @BlankLineBefore
        CommandConfig workbench = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " workbench", "/workbench"), DependencyVersions.PROJECT_ID + ".command.workbench");

        @BlankLineBefore
        CommandConfig world = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " world", "/world"), DependencyVersions.PROJECT_ID + ".command.world");

        // feature: 模块自带的命令, 插件启用时全部注册, 模块未启用时对玩家隐藏, 按模块 ID 排序
        @BlankLineBefore
        CommandConfig back = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " back", "/back"), DependencyVersions.PROJECT_ID + ".command.back");

        // ban 模块
        @BlankLineBefore
        CommandConfig ban = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " ban", "/ban"), DependencyVersions.PROJECT_ID + ".command.ban");

        @BlankLineBefore
        @YamlProperty("ban-history")
        CommandConfig banHistory = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " ban-history", "/ban-history"), DependencyVersions.PROJECT_ID + ".command.ban-history");

        @BlankLineBefore
        @YamlProperty("ban-ip")
        CommandConfig banIp = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " ban-ip", "/ban-ip"), DependencyVersions.PROJECT_ID + ".command.ban-ip");

        @BlankLineBefore
        CommandConfig unban = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " unban", "/unban"), DependencyVersions.PROJECT_ID + ".command.unban");

        @BlankLineBefore
        CommandConfig bed = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " bed", "/bed"), DependencyVersions.PROJECT_ID + ".command.bed");

        @BlankLineBefore
        CommandConfig head = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " head", "/head"), DependencyVersions.PROJECT_ID + ".command.head");

        @BlankLineBefore
        CommandConfig highlight = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " highlight", "/highlight"), DependencyVersions.PROJECT_ID + ".command.highlight");

        @BlankLineBefore
        CommandConfig maintenance = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " maintenance", "/maintenance"), DependencyVersions.PROJECT_ID + ".command.maintenance");

        @BlankLineBefore
        CommandConfig patrol = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " patrol", "/patrol"), DependencyVersions.PROJECT_ID + ".command.patrol");

        // player-limit 模块
        @BlankLineBefore
        @YamlProperty("max-players")
        CommandConfig maxPlayers = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " max-players", "/max-players"), DependencyVersions.PROJECT_ID + ".command.max-players");

        @BlankLineBefore
        CommandConfig server = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " server", "/server"), DependencyVersions.PROJECT_ID + ".command.server");

        @BlankLineBefore
        @YamlProperty("home")
        CommandConfig home = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " home", "/home"), DependencyVersions.PROJECT_ID + ".command.home");

        @BlankLineBefore
        @YamlProperty("set-home")
        CommandConfig setHome = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " set-home", "/set-home", "/sethome"), DependencyVersions.PROJECT_ID + ".command.set-home");

        @BlankLineBefore
        @YamlProperty("del-home")
        CommandConfig delHome = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " del-home", "/del-home", "/delhome"), DependencyVersions.PROJECT_ID + ".command.del-home");

        @BlankLineBefore
        @YamlProperty("del-all-home")
        CommandConfig delAllHome = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " del-all-home", "/del-all-home"), DependencyVersions.PROJECT_ID + ".command.del-all-home");

        @BlankLineBefore
        @YamlProperty("home-list")
        CommandConfig homeList = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " home-list", "/home-list", "/homelist"), DependencyVersions.PROJECT_ID + ".command.home-list");

        @BlankLineBefore
        @YamlProperty("edit-home")
        CommandConfig editHome = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " edit-home", "/edit-home", "/edithome"), DependencyVersions.PROJECT_ID + ".command.edit-home");

        // warp 模块
        @BlankLineBefore
        CommandConfig warp = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " warp", "/warp"), DependencyVersions.PROJECT_ID + ".command.warp");

        @BlankLineBefore
        @YamlProperty("set-warp")
        CommandConfig setWarp = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " set-warp", "/set-warp"), DependencyVersions.PROJECT_ID + ".command.set-warp");

        @BlankLineBefore
        @YamlProperty("del-warp")
        CommandConfig delWarp = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " del-warp", "/del-warp"), DependencyVersions.PROJECT_ID + ".command.del-warp");

        @BlankLineBefore
        @YamlProperty("warp-list")
        CommandConfig warpList = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " warp-list", "/warp-list"), DependencyVersions.PROJECT_ID + ".command.warp-list");

        @BlankLineBefore
        @YamlProperty("edit-warp")
        CommandConfig editWarp = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " edit-warp", "/edit-warp"), DependencyVersions.PROJECT_ID + ".command.edit-warp");

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
                // base
                case "reload" -> this.reload;
                case "feature_enable" -> this.featureEnable;
                case "feature_disable" -> this.featureDisable;
                case "feature_list" -> this.featureList;
                // a-z
                case "actionbar" -> this.actionbar;
                case "anvil" -> this.anvil;
                case "bed" -> this.bed;
                case "broadcast" -> this.broadcast;
                case "burn" -> this.burn;
                case "cartography-table" -> this.cartographyTable;
                case "color" -> this.color;
                case "credits" -> this.credits;
                case "custom-model-data" -> this.customModelData;
                case "custom-name" -> this.customName;
                case "demo" -> this.demo;
                case "distance" -> this.distance;
                case "enchant" -> this.enchant;
                case "enchantment-table" -> this.enchantmentTable;
                case "ender-chest" -> this.enderChest;
                case "extinguish" -> this.extinguish;
                case "feed" -> this.feed;
                case "fly" -> this.fly;
                case "fly-speed" -> this.flySpeed;
                case "grindstone" -> this.grindstone;
                case "hat" -> this.hat;
                case "heal" -> this.heal;
                case "ip" -> this.ip;
                case "ip-history" -> this.ipHistory;
                case "item-data" -> this.itemData;
                case "item-lore" -> this.itemLore;
                case "item-name" -> this.itemName;
                case "kick" -> this.kick;
                case "knockback" -> this.knockback;
                case "look" -> this.look;
                case "more" -> this.more;
                case "player-name" -> this.playerName;
                case "player-uuid" -> this.playerUuid;
                case "smithing-table" -> this.smithingTable;
                case "stonecutter" -> this.stonecutter;
                case "sudo" -> this.sudo;
                case "suicide" -> this.suicide;
                case "title" -> this.title;
                case "toast" -> this.toast;
                case "top-block" -> this.topBlock;
                case "totem-animation" -> this.totemAnimation;
                case "tp-offline" -> this.tpOffline;
                case "walk-speed" -> this.walkSpeed;
                case "workbench" -> this.workbench;
                case "world" -> this.world;
                // feature
                case "back" -> this.back;
                case "ban" -> this.ban;
                case "ban-history" -> this.banHistory;
                case "ban-ip" -> this.banIp;
                case "unban" -> this.unban;
                case "head" -> this.head;
                case "highlight" -> this.highlight;
                case "maintenance" -> this.maintenance;
                case "patrol" -> this.patrol;
                case "max-players" -> this.maxPlayers;
                case "server" -> this.server;
                case "warp" -> this.warp;
                case "home" -> this.home;
                case "set-home" -> this.setHome;
                case "del-home" -> this.delHome;
                case "del-all-home" -> this.delAllHome;
                case "home-list" -> this.homeList;
                case "edit-home" -> this.editHome;
                case "set-warp" -> this.setWarp;
                case "del-warp" -> this.delWarp;
                case "warp-list" -> this.warpList;
                case "edit-warp" -> this.editWarp;
                default -> throw new IllegalArgumentException("Unknown default command feature: " + featureID);
            };
        }
    }
}

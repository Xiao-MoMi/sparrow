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
        YamlMapperFactory mapperFactory = YamlMapperFactory.builder()
                .sparrowYaml(sparrowYaml)
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

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
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
                List.of("/" + DependencyVersions.PROJECT_ID + " reload", "/spr reload"),
                DependencyVersions.PROJECT_ID + ".command.admin.reload"
        );

        @BlankLineBefore
        CommandConfig featureEnable = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " feature_enable", "/spr feature_enable"),
                DependencyVersions.PROJECT_ID + ".command.admin.feature"
        );

        @BlankLineBefore
        CommandConfig featureDisable = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " feature_disable", "/spr feature_disable"),
                DependencyVersions.PROJECT_ID + ".command.admin.feature"
        );

        @BlankLineBefore
        CommandConfig featureList = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " feature_list", "/spr feature_list"),
                DependencyVersions.PROJECT_ID + ".command.admin.feature"
        );

        @BlankLineBefore
        CommandConfig anvil = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " anvil", "/spr anvil", "/anvil"),
                DependencyVersions.PROJECT_ID + ".command.anvil"
        );

        @BlankLineBefore
        @YamlProperty("cartography-table")
        CommandConfig cartographyTable = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " cartography_table", "/spr cartography_table", "/cartography_table"),
                DependencyVersions.PROJECT_ID + ".command.cartography_table"
        );

        @BlankLineBefore
        @YamlProperty("enchantment-table")
        CommandConfig enchantmentTable = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " enchantment_table", "/spr enchantment_table", "/enchantment_table"),
                DependencyVersions.PROJECT_ID + ".command.enchantment_table"
        );

        @BlankLineBefore
        CommandConfig grindstone = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " grindstone", "/spr grindstone", "/grindstone"),
                DependencyVersions.PROJECT_ID + ".command.grindstone"
        );

        @BlankLineBefore
        CommandConfig loom = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " loom", "/spr loom", "/loom"),
                DependencyVersions.PROJECT_ID + ".command.loom"
        );

        @BlankLineBefore
        @YamlProperty("smithing-table")
        CommandConfig smithingTable = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " smithing_table", "/spr smithing_table", "/smithing_table"),
                DependencyVersions.PROJECT_ID + ".command.smithing_table"
        );

        @BlankLineBefore
        CommandConfig stonecutter = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " stonecutter", "/spr stonecutter", "/stonecutter"),
                DependencyVersions.PROJECT_ID + ".command.stonecutter"
        );

        @BlankLineBefore
        CommandConfig workbench = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " workbench", "/spr workbench", "/workbench"),
                DependencyVersions.PROJECT_ID + ".command.workbench"
        );

        @BlankLineBefore
        CommandConfig air = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " air", "/spr air", "/air"),
                DependencyVersions.PROJECT_ID + ".command.air"
        );

        @BlankLineBefore
        CommandConfig burn = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " burn", "/spr burn", "/burn"),
                DependencyVersions.PROJECT_ID + ".command.burn"
        );

        @BlankLineBefore
        @YamlProperty("clear-chat")
        CommandConfig clearChat = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " clear_chat", "/spr clear_chat", "/clear_chat"),
                DependencyVersions.PROJECT_ID + ".command.clear_chat"
        );

        @BlankLineBefore
        @YamlProperty("clear-ender")
        CommandConfig clearEnder = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " clear_ender", "/spr clear_ender", "/clear_ender"),
                DependencyVersions.PROJECT_ID + ".command.clear_ender"
        );

        @BlankLineBefore
        @YamlProperty("clear-inv")
        CommandConfig clearInv = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " clear_inv", "/spr clear_inv", "/clear_inv"),
                DependencyVersions.PROJECT_ID + ".command.clear_inv"
        );

        @BlankLineBefore
        CommandConfig credits = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " credits", "/spr credits", "/credits"),
                DependencyVersions.PROJECT_ID + ".command.credits"
        );

        @BlankLineBefore
        CommandConfig demo = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " demo", "/spr demo", "/demo"),
                DependencyVersions.PROJECT_ID + ".command.demo"
        );

        @BlankLineBefore
        CommandConfig distance = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " distance", "/spr distance", "/distance"),
                DependencyVersions.PROJECT_ID + ".command.distance"
        );

        @BlankLineBefore
        @YamlProperty("ender-chest")
        CommandConfig enderChest = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " ender_chest", "/spr ender_chest", "/ender_chest"),
                DependencyVersions.PROJECT_ID + ".command.ender_chest"
        );

        @BlankLineBefore
        CommandConfig extinguish = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " extinguish", "/spr extinguish", "/extinguish"),
                DependencyVersions.PROJECT_ID + ".command.extinguish"
        );

        @BlankLineBefore
        CommandConfig feed = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " feed", "/spr feed", "/feed"),
                DependencyVersions.PROJECT_ID + ".command.feed"
        );

        @BlankLineBefore
        CommandConfig fly = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " fly", "/spr fly", "/fly"),
                DependencyVersions.PROJECT_ID + ".command.fly"
        );

        @BlankLineBefore
        @YamlProperty("fly-speed")
        CommandConfig flySpeed = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " fly_speed", "/spr fly_speed", "/fly_speed"),
                DependencyVersions.PROJECT_ID + ".command.fly_speed"
        );

        @BlankLineBefore
        @YamlProperty("force-say")
        CommandConfig forceSay = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " force_say", "/spr force_say", "/force_say"),
                DependencyVersions.PROJECT_ID + ".command.force_say"
        );

        @BlankLineBefore
        CommandConfig hat = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " hat", "/spr hat", "/hat"),
                DependencyVersions.PROJECT_ID + ".command.hat"
        );

        @BlankLineBefore
        CommandConfig heal = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " heal", "/spr heal", "/heal"),
                DependencyVersions.PROJECT_ID + ".command.heal"
        );

        @BlankLineBefore
        CommandConfig knockback = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " knockback", "/spr knockback", "/knockback"),
                DependencyVersions.PROJECT_ID + ".command.knockback"
        );

        @BlankLineBefore
        CommandConfig look = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " look", "/spr look", "/look"),
                DependencyVersions.PROJECT_ID + ".command.look"
        );

        @BlankLineBefore
        CommandConfig more = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " more", "/spr more", "/more"),
                DependencyVersions.PROJECT_ID + ".command.more"
        );

        @BlankLineBefore
        CommandConfig repair = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " repair", "/spr repair", "/repair"),
                DependencyVersions.PROJECT_ID + ".command.repair"
        );

        @BlankLineBefore
        @YamlProperty("repair-all")
        CommandConfig repairAll = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " repair_all", "/spr repair_all", "/repair_all"),
                DependencyVersions.PROJECT_ID + ".command.repair_all"
        );

        @BlankLineBefore
        CommandConfig server = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " server", "/spr server", "/server"),
                DependencyVersions.PROJECT_ID + ".command.server"
        );

        @BlankLineBefore
        CommandConfig smite = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " smite", "/spr smite", "/smite"),
                DependencyVersions.PROJECT_ID + ".command.smite"
        );

        @BlankLineBefore
        CommandConfig sudo = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " sudo", "/spr sudo", "/sudo"),
                DependencyVersions.PROJECT_ID + ".command.sudo"
        );

        @BlankLineBefore
        CommandConfig suicide = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " suicide", "/spr suicide", "/suicide"),
                DependencyVersions.PROJECT_ID + ".command.suicide"
        );

        @BlankLineBefore
        @YamlProperty("top-block")
        CommandConfig topBlock = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " top_block", "/spr top_block", "/top_block"),
                DependencyVersions.PROJECT_ID + ".command.top_block"
        );

        @BlankLineBefore
        @YamlProperty("totem-animation")
        CommandConfig totemAnimation = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " totem_animation", "/spr totem_animation", "/totem_animation"),
                DependencyVersions.PROJECT_ID + ".command.totem_animation"
        );

        @BlankLineBefore
        @YamlProperty("walk-speed")
        CommandConfig walkSpeed = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " walk_speed", "/spr walk_speed", "/walk_speed"),
                DependencyVersions.PROJECT_ID + ".command.walk_speed"
        );

        @BlankLineBefore
        CommandConfig color = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " color", "/spr color", "/color"),
                DependencyVersions.PROJECT_ID + ".command.color"
        );

        @BlankLineBefore
        @YamlProperty("custom-model-data")
        CommandConfig customModelData = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " custom_model_data", "/spr custom_model_data", "/custom_model_data"),
                DependencyVersions.PROJECT_ID + ".command.custom_model_data"
        );

        @BlankLineBefore
        @YamlProperty("custom-name")
        CommandConfig customName = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " custom_name", "/spr custom_name", "/custom_name"),
                DependencyVersions.PROJECT_ID + ".command.custom_name"
        );

        @BlankLineBefore
        CommandConfig enchant = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " enchant", "/spr enchant", "/enchant"),
                DependencyVersions.PROJECT_ID + ".command.enchant"
        );

        @BlankLineBefore
        @YamlProperty("item-damage")
        CommandConfig itemDamage = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " item_damage", "/spr item_damage", "/item_damage"),
                DependencyVersions.PROJECT_ID + ".command.item_damage"
        );

        @BlankLineBefore
        @YamlProperty("item-data")
        CommandConfig itemData = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " item_data", "/spr item_data", "/item_data"),
                DependencyVersions.PROJECT_ID + ".command.item_data"
        );

        @BlankLineBefore
        @YamlProperty("item-name")
        CommandConfig itemName = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " item_name", "/spr item_name", "/item_name"),
                DependencyVersions.PROJECT_ID + ".command.item_name"
        );

        @BlankLineBefore
        CommandConfig kick = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " kick", "/spr kick", "/kick"),
                DependencyVersions.PROJECT_ID + ".command.kick"
        );

        @BlankLineBefore
        @YamlProperty("ip-history")
        CommandConfig ipHistory = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " ip_history", "/spr ip_history", "/ip_history"),
                DependencyVersions.PROJECT_ID + ".command.ip_history"
        );

        @BlankLineBefore
        @YamlProperty("name-history")
        CommandConfig nameHistory = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " name_history", "/spr name_history", "/name_history"),
                DependencyVersions.PROJECT_ID + ".command.name_history"
        );

        @BlankLineBefore
        @YamlProperty("player-name")
        CommandConfig playerName = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " player_name", "/spr player_name", "/player_name"),
                DependencyVersions.PROJECT_ID + ".command.player_name"
        );

        @BlankLineBefore
        @YamlProperty("player-uuid")
        CommandConfig playerUuid = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " player_uuid", "/spr player_uuid", "/player_uuid"),
                DependencyVersions.PROJECT_ID + ".command.player_uuid"
        );

        @BlankLineBefore
        CommandConfig respawn = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " respawn", "/spr respawn", "/respawn"),
                DependencyVersions.PROJECT_ID + ".command.respawn"
        );

        @BlankLineBefore
        CommandConfig world = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " world", "/spr world", "/world"),
                DependencyVersions.PROJECT_ID + ".command.world"
        );

        @BlankLineBefore
        CommandConfig actionbar = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " actionbar", "/spr actionbar", "/actionbar"),
                DependencyVersions.PROJECT_ID + ".command.actionbar"
        );

        @BlankLineBefore
        CommandConfig title = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " title", "/spr title", "/title"),
                DependencyVersions.PROJECT_ID + ".command.title"
        );

        @BlankLineBefore
        CommandConfig toast = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " toast", "/spr toast", "/toast"),
                DependencyVersions.PROJECT_ID + ".command.toast"
        );

        @BlankLineBefore
        CommandConfig broadcast = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " broadcast", "/spr broadcast", "/broadcast"),
                DependencyVersions.PROJECT_ID + ".command.broadcast"
        );

        @BlankLineBefore
        @YamlProperty("item-lore")
        CommandConfig itemLore = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " item_lore", "/spr item_lore", "/item_lore"),
                DependencyVersions.PROJECT_ID + ".command.item_lore"
        );

        @BlankLineBefore
        @YamlProperty("tp-offline")
        CommandConfig tpOffline = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " tp_offline", "/spr tp_offline", "/tp_offline"),
                DependencyVersions.PROJECT_ID + ".command.tp_offline"
        );

        @BlankLineBefore
        @YamlProperty("max-players")
        CommandConfig maxPlayers = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " max_players", "/spr max_players", "/max_players"),
                DependencyVersions.PROJECT_ID + ".command.max_players"
        );

        @BlankLineBefore
        CommandConfig patrol = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " patrol", "/spr patrol", "/patrol"),
                DependencyVersions.PROJECT_ID + ".command.patrol"
        );

        @BlankLineBefore
        CommandConfig tpa = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " tpa", "/spr tpa", "/tpa"),
                DependencyVersions.PROJECT_ID + ".command.tpa"
        );

        @BlankLineBefore
        @YamlProperty("tpa-here")
        CommandConfig tpaHere = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " tpa_here", "/spr tpa_here", "/tpa_here"),
                DependencyVersions.PROJECT_ID + ".command.tpa_here"
        );

        @BlankLineBefore
        @YamlProperty("tpa-accept")
        CommandConfig tpaAccept = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " tpa_accept", "/spr tpa_accept", "/tpa_accept"),
                DependencyVersions.PROJECT_ID + ".command.tpa_accept"
        );

        @BlankLineBefore
        @YamlProperty("tpa-deny")
        CommandConfig tpaDeny = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " tpa_deny", "/spr tpa_deny", "/tpa_deny"),
                DependencyVersions.PROJECT_ID + ".command.tpa_deny"
        );

        @BlankLineBefore
        @YamlProperty("tpa-cancel")
        CommandConfig tpaCancel = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " tpa_cancel", "/spr tpa_cancel", "/tpa_cancel"),
                DependencyVersions.PROJECT_ID + ".command.tpa_cancel"
        );

        @BlankLineBefore
        CommandConfig home = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " home", "/spr home", "/home"),
                DependencyVersions.PROJECT_ID + ".command.home"
        );

        @BlankLineBefore
        @YamlProperty("set-home")
        CommandConfig setHome = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " set_home", "/spr set_home", "/set_home"),
                DependencyVersions.PROJECT_ID + ".command.set_home"
        );

        @BlankLineBefore
        @YamlProperty("del-home")
        CommandConfig delHome = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " del_home", "/spr del_home", "/del_home"),
                DependencyVersions.PROJECT_ID + ".command.del_home"
        );

        @BlankLineBefore
        @YamlProperty("edit-home")
        CommandConfig editHome = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " edit_home", "/spr edit_home", "/edit_home"),
                DependencyVersions.PROJECT_ID + ".command.edit_home"
        );

        @BlankLineBefore
        @YamlProperty("home-list")
        CommandConfig homeList = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " home_list", "/spr home_list", "/home_list"),
                DependencyVersions.PROJECT_ID + ".command.home_list"
        );

        @BlankLineBefore
        CommandConfig warp = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " warp", "/spr warp", "/warp"),
                DependencyVersions.PROJECT_ID + ".command.warp"
        );

        @BlankLineBefore
        @YamlProperty("set-warp")
        CommandConfig setWarp = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " set_warp", "/spr set_warp", "/set_warp"),
                DependencyVersions.PROJECT_ID + ".command.set_warp"
        );

        @BlankLineBefore
        @YamlProperty("del-warp")
        CommandConfig delWarp = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " del_warp", "/spr del_warp", "/del_warp"),
                DependencyVersions.PROJECT_ID + ".command.del_warp"
        );

        @BlankLineBefore
        @YamlProperty("edit-warp")
        CommandConfig editWarp = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " edit_warp", "/spr edit_warp", "/edit_warp"),
                DependencyVersions.PROJECT_ID + ".command.edit_warp"
        );

        @BlankLineBefore
        @YamlProperty("warp-list")
        CommandConfig warpList = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " warp_list", "/spr warp_list", "/warp_list"),
                DependencyVersions.PROJECT_ID + ".command.warp_list"
        );

        @BlankLineBefore
        CommandConfig spawn = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " spawn", "/spr spawn", "/spawn"),
                DependencyVersions.PROJECT_ID + ".command.spawn"
        );

        @BlankLineBefore
        CommandConfig head = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " head", "/spr head", "/head"),
                DependencyVersions.PROJECT_ID + ".command.head"
        );

        @BlankLineBefore
        CommandConfig maintenance = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " maintenance", "/spr maintenance", "/maintenance"),
                DependencyVersions.PROJECT_ID + ".command.maintenance"
        );

        @BlankLineBefore
        CommandConfig highlight = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " highlight", "/spr highlight", "/highlight"),
                DependencyVersions.PROJECT_ID + ".command.highlight"
        );

        @BlankLineBefore
        CommandConfig back = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " back", "/spr back", "/back"),
                DependencyVersions.PROJECT_ID + ".command.back"
        );

        @BlankLineBefore
        @YamlProperty("death-back")
        CommandConfig deathBack = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " death-back", "/spr death-back", "/death-back"),
                DependencyVersions.PROJECT_ID + ".command.death-back"
        );

        @BlankLineBefore
        CommandConfig rtp = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " rtp", "/spr rtp", "/rtp"),
                DependencyVersions.PROJECT_ID + ".command.rtp"
        );

        @BlankLineBefore
        @YamlProperty("rtp-range")
        CommandConfig rtpRange = new CommandConfig(
                true,
                List.of("/" + DependencyVersions.PROJECT_ID + " rtp_range", "/spr rtp_range", "/rtp_range"),
                DependencyVersions.PROJECT_ID + ".command.rtp_range"
        );

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
                case "air" -> this.air;
                case "anvil" -> this.anvil;
                case "broadcast" -> this.broadcast;
                case "burn" -> this.burn;
                case "cartography_table" -> this.cartographyTable;
                case "clear_chat" -> this.clearChat;
                case "clear_ender" -> this.clearEnder;
                case "clear_inv" -> this.clearInv;
                case "color" -> this.color;
                case "credits" -> this.credits;
                case "custom_model_data" -> this.customModelData;
                case "custom_name" -> this.customName;
                case "demo" -> this.demo;
                case "distance" -> this.distance;
                case "enchant" -> this.enchant;
                case "enchantment_table" -> this.enchantmentTable;
                case "ender_chest" -> this.enderChest;
                case "extinguish" -> this.extinguish;
                case "feed" -> this.feed;
                case "fly" -> this.fly;
                case "fly_speed" -> this.flySpeed;
                case "force_say" -> this.forceSay;
                case "grindstone" -> this.grindstone;
                case "hat" -> this.hat;
                case "heal" -> this.heal;
                case "ip_history" -> this.ipHistory;
                case "item_damage" -> this.itemDamage;
                case "item_data" -> this.itemData;
                case "item_lore" -> this.itemLore;
                case "item_name" -> this.itemName;
                case "kick" -> this.kick;
                case "knockback" -> this.knockback;
                case "look" -> this.look;
                case "loom" -> this.loom;
                case "more" -> this.more;
                case "name_history" -> this.nameHistory;
                case "player_name" -> this.playerName;
                case "player_uuid" -> this.playerUuid;
                case "repair" -> this.repair;
                case "repair_all" -> this.repairAll;
                case "server" -> this.server;
                case "smite" -> this.smite;
                case "smithing_table" -> this.smithingTable;
                case "stonecutter" -> this.stonecutter;
                case "sudo" -> this.sudo;
                case "suicide" -> this.suicide;
                case "title" -> this.title;
                case "toast" -> this.toast;
                case "top_block" -> this.topBlock;
                case "totem_animation" -> this.totemAnimation;
                case "tp_offline" -> this.tpOffline;
                case "walk_speed" -> this.walkSpeed;
                case "workbench" -> this.workbench;
                case "respawn" -> this.respawn;
                case "world" -> this.world;
                // feature
                case "highlight" -> this.highlight;
                case "max_players" -> this.maxPlayers;
                case "maintenance" -> this.maintenance;
                case "head" -> this.head;
                case "spawn" -> this.spawn;
                case "home" -> this.home;
                case "set_home" -> this.setHome;
                case "del_home" -> this.delHome;
                case "edit_home" -> this.editHome;
                case "home_list" -> this.homeList;
                case "warp" -> this.warp;
                case "set_warp" -> this.setWarp;
                case "del_warp" -> this.delWarp;
                case "edit_warp" -> this.editWarp;
                case "warp_list" -> this.warpList;
                case "tpa" -> this.tpa;
                case "tpa_here" -> this.tpaHere;
                case "tpa_accept" -> this.tpaAccept;
                case "tpa_deny" -> this.tpaDeny;
                case "tpa_cancel" -> this.tpaCancel;
                case "patrol" -> this.patrol;
                case "back" -> this.back;
                case "death-back" -> this.deathBack;
                case "rtp" -> this.rtp;
                case "rtp_range" -> this.rtpRange;
                default -> throw new IllegalArgumentException("Unknown default command feature: " + featureID);
            };
        }
    }
}
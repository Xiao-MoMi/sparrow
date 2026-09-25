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

        @BlankLineBefore
        CommandConfig workbench = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " workbench", "/workbench"), DependencyVersions.PROJECT_ID + ".command.workbench");

        @BlankLineBefore
        CommandConfig anvil = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " anvil", "/anvil"), DependencyVersions.PROJECT_ID + ".command.anvil");

        @BlankLineBefore
        CommandConfig grindstone = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " grindstone", "/grindstone"), DependencyVersions.PROJECT_ID + ".command.grindstone");

        @BlankLineBefore
        @YamlProperty("smithing-table")
        CommandConfig smithingTable = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " smithing-table", "/smithing-table"), DependencyVersions.PROJECT_ID + ".command.smithing-table");

        @BlankLineBefore
        CommandConfig stonecutter = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " stonecutter", "/stonecutter"), DependencyVersions.PROJECT_ID + ".command.stonecutter");

        @BlankLineBefore
        @YamlProperty("cartography-table")
        CommandConfig cartographyTable = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " cartography-table", "/cartography-table"), DependencyVersions.PROJECT_ID + ".command.cartography-table");

        @BlankLineBefore
        CommandConfig heal = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " heal", "/heal"), DependencyVersions.PROJECT_ID + ".command.heal");

        @BlankLineBefore
        CommandConfig feed = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " feed", "/feed"), DependencyVersions.PROJECT_ID + ".command.feed");

        @BlankLineBefore
        @YamlProperty("fly-speed")
        CommandConfig flySpeed = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " fly-speed", "/fly-speed"), DependencyVersions.PROJECT_ID + ".command.fly-speed");

        @BlankLineBefore
        @YamlProperty("walk-speed")
        CommandConfig walkSpeed = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " walk-speed", "/walk-speed"), DependencyVersions.PROJECT_ID + ".command.walk-speed");

        @BlankLineBefore
        CommandConfig suicide = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " suicide", "/suicide"), DependencyVersions.PROJECT_ID + ".command.suicide");

        @BlankLineBefore
        CommandConfig burn = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " burn", "/burn"), DependencyVersions.PROJECT_ID + ".command.burn");

        @BlankLineBefore
        CommandConfig extinguish = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " extinguish", "/extinguish"), DependencyVersions.PROJECT_ID + ".command.extinguish");

        @BlankLineBefore
        CommandConfig sudo = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " sudo", "/sudo"), DependencyVersions.PROJECT_ID + ".command.sudo");

        @BlankLineBefore
        CommandConfig look = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " look", "/look"), DependencyVersions.PROJECT_ID + ".command.look");

        @BlankLineBefore
        @YamlProperty("top-block")
        CommandConfig topBlock = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " top-block", "/top-block"), DependencyVersions.PROJECT_ID + ".command.top-block");

        @BlankLineBefore
        CommandConfig fly = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " fly", "/fly"), DependencyVersions.PROJECT_ID + ".command.fly");

        @BlankLineBefore
        CommandConfig toast = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " toast", "/toast"), DependencyVersions.PROJECT_ID + ".command.toast");

        @BlankLineBefore
        CommandConfig actionbar = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " actionbar", "/actionbar"), DependencyVersions.PROJECT_ID + ".command.actionbar");

        @BlankLineBefore
        CommandConfig broadcast = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " broadcast", "/broadcast"), DependencyVersions.PROJECT_ID + ".command.broadcast");

        @BlankLineBefore
        CommandConfig title = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " title", "/title"), DependencyVersions.PROJECT_ID + ".command.title");

        @BlankLineBefore
        @YamlProperty("totem-animation")
        CommandConfig totemAnimation = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " totem-animation", "/totem-animation"), DependencyVersions.PROJECT_ID + ".command.totem-animation");

        @BlankLineBefore
        CommandConfig demo = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " demo", "/demo"), DependencyVersions.PROJECT_ID + ".command.demo");

        @BlankLineBefore
        CommandConfig credits = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " credits", "/credits"), DependencyVersions.PROJECT_ID + ".command.credits");

        @BlankLineBefore
        CommandConfig patrol = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " patrol", "/patrol"), DependencyVersions.PROJECT_ID + ".command.patrol");

        @BlankLineBefore
        CommandConfig highlight = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " highlight", "/highlight"), DependencyVersions.PROJECT_ID + ".command.highlight");

        @BlankLineBefore
        CommandConfig server = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " server", "/server"), DependencyVersions.PROJECT_ID + ".command.server");

        @BlankLineBefore
        CommandConfig enchant = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " enchant", "/enchant"), DependencyVersions.PROJECT_ID + ".command.enchant");

        @BlankLineBefore
        @YamlProperty("enchantment-table")
        CommandConfig enchantmentTable = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " enchantment-table", "/enchantment-table"), DependencyVersions.PROJECT_ID + ".command.enchantment-table");

        @BlankLineBefore
        @YamlProperty("item-data")
        CommandConfig itemData = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " item_data", "/item_data"), DependencyVersions.PROJECT_ID + ".command.item-data");

        @BlankLineBefore
        CommandConfig color = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " color", "/color"), DependencyVersions.PROJECT_ID + ".command.color");

        @BlankLineBefore
        @YamlProperty("custom-model-data")
        CommandConfig customModelData = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " custom_model_data", "/custom_model_data"), DependencyVersions.PROJECT_ID + ".command.custom-model-data");

        @BlankLineBefore
        CommandConfig more = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " more", "/more"), DependencyVersions.PROJECT_ID + ".command.more");

        @BlankLineBefore
        CommandConfig distance = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " distance", "/distance"), DependencyVersions.PROJECT_ID + ".command.distance");

        @BlankLineBefore
        @YamlProperty("custom-name")
        CommandConfig customName = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " custom_name", "/custom_name"), DependencyVersions.PROJECT_ID + ".command.custom-name");

        @BlankLineBefore
        @YamlProperty("item-lore")
        CommandConfig itemLore = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " item_lore", "/item_lore"), DependencyVersions.PROJECT_ID + ".command.item-lore");

        @BlankLineBefore
        @YamlProperty("item-name")
        CommandConfig itemName = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " item_name", "/item_name"), DependencyVersions.PROJECT_ID + ".command.item-name");

        @BlankLineBefore
        @YamlProperty("ender-chest")
        CommandConfig enderChest = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " ender_chest", "/ender_chest"), DependencyVersions.PROJECT_ID + ".command.ender-chest");

        @BlankLineBefore
        @YamlProperty("tp-offline")
        CommandConfig tpOffline = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " tp-offline", "/tp-offline"), DependencyVersions.PROJECT_ID + ".command.tp-offline");

        @BlankLineBefore
        CommandConfig world = new CommandConfig(true, List.of("/" + DependencyVersions.PROJECT_ID + " world", "/world"), DependencyVersions.PROJECT_ID + ".command.world");

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
                case "reload" -> this.reload;
                case "fly" -> this.fly;
                case "toast" -> this.toast;
                case "actionbar" -> this.actionbar;
                case "broadcast" -> this.broadcast;
                case "title" -> this.title;
                case "totem-animation" -> this.totemAnimation;
                case "demo" -> this.demo;
                case "credits" -> this.credits;
                case "patrol" -> this.patrol;
                case "highlight" -> this.highlight;
                case "item-name" -> this.itemName;
                case "item-lore" -> this.itemLore;
                case "custom-name" -> this.customName;
                case "ender-chest" -> this.enderChest;
                case "item-data" -> this.itemData;
                case "color" -> this.color;
                case "custom-model-data" -> this.customModelData;
                case "more" -> this.more;
                case "distance" -> this.distance;
                case "server" -> this.server;
                case "enchant" -> this.enchant;
                case "enchantment-table" -> this.enchantmentTable;
                case "tp-offline" -> this.tpOffline;
                case "world" -> this.world;
                case "fly-speed" -> this.flySpeed;
                case "walk-speed" -> this.walkSpeed;
                case "suicide" -> this.suicide;
                case "burn" -> this.burn;
                case "extinguish" -> this.extinguish;
                case "sudo" -> this.sudo;
                case "look" -> this.look;
                case "top-block" -> this.topBlock;
                case "workbench" -> this.workbench;
                case "anvil" -> this.anvil;
                case "grindstone" -> this.grindstone;
                case "smithing-table" -> this.smithingTable;
                case "stonecutter" -> this.stonecutter;
                case "cartography-table" -> this.cartographyTable;
                case "heal" -> this.heal;
                case "feed" -> this.feed;
                case "feature_enable" -> this.featureEnable;
                case "feature_disable" -> this.featureDisable;
                case "feature_list" -> this.featureList;
                default -> throw new IllegalArgumentException("Unknown default command feature: " + featureID);
            };
        }
    }
}

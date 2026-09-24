package net.momirealms.sparrow.plugin.command;

import net.momirealms.sparrow.player.SparrowPlayer;
import net.kyori.adventure.util.Index;
import net.momirealms.sparrow.plugin.command.feature.ReloadCommand;
import net.momirealms.sparrow.plugin.command.feature.FlySpeedCommand;
import net.momirealms.sparrow.plugin.command.feature.WalkSpeedCommand;
import net.momirealms.sparrow.plugin.command.feature.SuicideCommand;
import net.momirealms.sparrow.plugin.command.feature.BurnCommand;
import net.momirealms.sparrow.plugin.command.feature.ExtinguishCommand;
import net.momirealms.sparrow.plugin.command.feature.SudoCommand;
import net.momirealms.sparrow.plugin.command.feature.LookCommand;
import net.momirealms.sparrow.plugin.command.feature.TopBlockCommand;
import net.momirealms.sparrow.plugin.command.feature.WorkbenchCommand;
import net.momirealms.sparrow.plugin.command.feature.AnvilCommand;
import net.momirealms.sparrow.plugin.command.feature.GrindstoneCommand;
import net.momirealms.sparrow.plugin.command.feature.SmithingTableCommand;
import net.momirealms.sparrow.plugin.command.feature.StonecutterCommand;
import net.momirealms.sparrow.plugin.command.feature.CartographyTableCommand;
import net.momirealms.sparrow.plugin.command.feature.LoomCommand;
import net.momirealms.sparrow.plugin.command.feature.HealCommand;
import net.momirealms.sparrow.plugin.command.feature.FeedCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureEnableCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureDisableCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureListCommand;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.setting.ManagerSetting;

import java.util.List;
import java.util.Locale;

public final class BukkitCommandManager extends AbstractCommandManager {
    public final SparrowPlugin plugin;
    private final Index<String, CommandFeature> index;

    public BukkitCommandManager(SparrowPlugin plugin) {
        // 构建 LegacyPaperCommandManager 并交给父类
        super(plugin, new LegacyPaperCommandManager<>(
                plugin.javaPlugin(),
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity()
        ));
        // 初始化命令索引
        this.plugin = plugin;
        this.index = Index.create(CommandFeature::getFeatureID, List.of(
                new ReloadCommand(this, plugin),
                new FlySpeedCommand(this, plugin),
                new WalkSpeedCommand(this, plugin),
                new SuicideCommand(this, plugin),
                new BurnCommand(this, plugin),
                new ExtinguishCommand(this, plugin),
                new SudoCommand(this, plugin),
                new LookCommand(this, plugin),
                new TopBlockCommand(this, plugin),
                new FeatureEnableCommand(this, plugin),
                new FeatureDisableCommand(this, plugin),
                new FeatureListCommand(this, plugin),
                new WorkbenchCommand(this, plugin),
                new AnvilCommand(this, plugin),
                new GrindstoneCommand(this, plugin),
                new SmithingTableCommand(this, plugin),
                new StonecutterCommand(this, plugin),
                new CartographyTableCommand(this, plugin),
                new LoomCommand(this, plugin),
                new HealCommand(this, plugin),
                new FeedCommand(this, plugin)
        ));
        final LegacyPaperCommandManager<CommandSender> manager = (LegacyPaperCommandManager<CommandSender>) getCommandManager();
        // 开启 ALLOW_UNSAFE_REGISTRATION, 以允许在部分运行环境中完成命令注册.
        manager.settings().set(ManagerSetting.ALLOW_UNSAFE_REGISTRATION, true);
        // 能力注册 brigadier 或异步补全.
        if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            manager.registerBrigadier();
            manager.brigadierManager().setNativeNumberSuggestions(true);
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }
    }

    @Override
    protected Locale getLocale(CommandSender sender) {
        if (sender instanceof Player player) {
            SparrowPlayer pluginPlayer = this.plugin.playerManager().getPlayer(player);
            return pluginPlayer == null ? null : pluginPlayer.locale();
        }
        return null;
    }

    @Override
    public Index<String, CommandFeature> features() {
        return this.index;
    }
}

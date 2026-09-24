package net.momirealms.sparrow.plugin.command;

import net.momirealms.sparrow.player.SparrowPlayer;
import net.kyori.adventure.util.Index;
import net.momirealms.sparrow.plugin.command.feature.ReloadCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureCommand;
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
                new FeatureCommand(this, plugin)
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

package net.momirealms.sparrow.plugin.command;

import net.momirealms.sparrow.player.SparrowPlayer;
import net.kyori.adventure.util.Index;
import net.momirealms.sparrow.plugin.command.feature.ReloadCommand;
import net.momirealms.sparrow.plugin.command.feature.EnchantCommand;
import net.momirealms.sparrow.plugin.command.feature.EnchantmentTableCommand;
import net.momirealms.sparrow.plugin.command.feature.WorldCommand;
import net.momirealms.sparrow.plugin.command.feature.TpOfflineCommand;
import net.momirealms.sparrow.plugin.command.feature.ActionBarCommand;
import net.momirealms.sparrow.plugin.command.feature.BroadcastCommand;
import net.momirealms.sparrow.plugin.command.feature.TitleCommand;
import net.momirealms.sparrow.plugin.command.feature.TotemAnimationCommand;
import net.momirealms.sparrow.plugin.command.feature.DemoCommand;
import net.momirealms.sparrow.plugin.command.feature.CreditsCommand;
import net.momirealms.sparrow.plugin.command.feature.FlySpeedCommand;
import net.momirealms.sparrow.plugin.command.feature.FlyCommand;
import net.momirealms.sparrow.plugin.command.feature.PatrolCommand;
import net.momirealms.sparrow.plugin.command.feature.HighlightCommand;
import net.momirealms.sparrow.plugin.command.feature.ItemDataCommand;
import net.momirealms.sparrow.plugin.command.feature.ItemNameCommand;
import net.momirealms.sparrow.plugin.command.feature.CustomNameCommand;
import net.momirealms.sparrow.plugin.command.feature.ColorCommand;
import net.momirealms.sparrow.plugin.command.feature.EnderChestCommand;
import net.momirealms.sparrow.plugin.command.feature.MoreCommand;
import net.momirealms.sparrow.plugin.command.feature.DistanceCommand;
import net.momirealms.sparrow.plugin.command.feature.ServerCommand;
import net.momirealms.sparrow.plugin.command.feature.ToastCommand;
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
import net.momirealms.sparrow.plugin.command.feature.HealCommand;
import net.momirealms.sparrow.plugin.command.feature.FeedCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureEnableCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureDisableCommand;
import net.momirealms.sparrow.plugin.command.feature.FeatureListCommand;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.ReflectionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.bukkit.internal.CraftBukkitReflection;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.setting.ManagerSetting;

import java.util.List;
import java.util.Locale;

public final class BukkitCommandManager extends AbstractCommandManager {
    public final SparrowPlugin plugin;
    private final Index<String, CommandFeature> index;

    static {
        // 无版本包名的 Spigot 没有 getMinecraftVersion(), Cloud 会因此禁用原生选择器.
        if (CraftBukkitReflection.MAJOR_REVISION == -1) {
            String[] version = Bukkit.getBukkitVersion().split("\\.");
            int major = Integer.parseInt(version[0]);
            int revision = major == 1 ? Integer.parseInt(version[1]) : major;
            try {
                ReflectionUtils.unreflectSetter(CraftBukkitReflection.class.getDeclaredField("MAJOR_REVISION")).invokeExact(revision);
            } catch (Throwable exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    public BukkitCommandManager(SparrowPlugin plugin) {
        this(plugin, new LegacyPaperCommandManager<>(
                plugin.javaPlugin(),
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity()
        ));
    }

    private BukkitCommandManager(SparrowPlugin plugin, LegacyPaperCommandManager<CommandSender> manager) {
        super(plugin, manager, manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER) || manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION));
        // 初始化命令索引
        this.plugin = plugin;
        this.index = Index.create(CommandFeature::getFeatureID, List.of(
                new AnvilCommand(this, plugin),
                new ActionBarCommand(this, plugin),
                new CreditsCommand(this, plugin),
                new CartographyTableCommand(this, plugin),
                new BroadcastCommand(this, plugin),
                new BurnCommand(this, plugin),
                new DemoCommand(this, plugin),
                new EnchantCommand(this, plugin),
                new EnchantmentTableCommand(this, plugin),
                new WorldCommand(this, plugin),
                new TpOfflineCommand(this, plugin),
                new ExtinguishCommand(this, plugin),
                new FeatureEnableCommand(this, plugin),
                new FeatureDisableCommand(this, plugin),
                new FeatureListCommand(this, plugin),
                new FeedCommand(this, plugin),
                new FlySpeedCommand(this, plugin),
                new FlyCommand(this, plugin),
                new GrindstoneCommand(this, plugin),
                new HealCommand(this, plugin),
                new LookCommand(this, plugin),
                new PatrolCommand(this, plugin),
                new HighlightCommand(this, plugin),
                new ItemDataCommand(this, plugin),
                new ItemNameCommand(this, plugin),
                new CustomNameCommand(this, plugin),
                new ColorCommand(this, plugin),
                new EnderChestCommand(this, plugin),
                new MoreCommand(this, plugin),
                new DistanceCommand(this, plugin),
                new ServerCommand(this, plugin),
                new ReloadCommand(this, plugin),
                new StonecutterCommand(this, plugin),
                new SuicideCommand(this, plugin),
                new SudoCommand(this, plugin),
                new SmithingTableCommand(this, plugin),
                new TitleCommand(this, plugin),
                new ToastCommand(this, plugin),
                new TotemAnimationCommand(this, plugin),
                new TopBlockCommand(this, plugin),
                new WalkSpeedCommand(this, plugin),
                new WorkbenchCommand(this, plugin)
        ));
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

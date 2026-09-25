package net.momirealms.sparrow.bukkit;

import net.momirealms.sparrow.bukkit.command.SparrowBukkitCommandManager;
import net.momirealms.sparrow.bukkit.feature.item.SparrowBukkitItemFactory;
import net.momirealms.sparrow.bukkit.feature.skull.SparrowBukkitSkullManager;
import net.momirealms.sparrow.common.command.SparrowCommandManager;
import net.momirealms.sparrow.common.dependency.Dependency;
import net.momirealms.sparrow.common.plugin.AbstractSparrowPlugin;
import net.momirealms.sparrow.common.sender.SenderFactory;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public final class SparrowBukkitPlugin extends AbstractSparrowPlugin {

    private static SparrowBukkitPlugin plugin;
    private final SparrowBukkitBootstrap bootstrap;
    private SparrowBukkitSkullManager skullManager;
    private SparrowBukkitSenderFactory senderFactory;
    private SparrowBukkitCommandManager commandManager;
    private SparrowBukkitItemFactory itemFactory;

    public SparrowBukkitPlugin(SparrowBukkitBootstrap bootstrap) {
        plugin = this;
        this.bootstrap = bootstrap;
    }

    @Override
    protected Set<Dependency> getGlobalDependencies() {
        Set<Dependency> dependencies = super.getGlobalDependencies();
        dependencies.add(Dependency.BSTATS_BUKKIT);
        dependencies.add(Dependency.CLOUD_BUKKIT);
        dependencies.add(Dependency.CLOUD_PAPER);
        return dependencies;
    }

    @Override
    public void enable() {
        super.enable();
        this.skullManager = new SparrowBukkitSkullManager(this);
        this.itemFactory = SparrowBukkitItemFactory.create(this);
        new Metrics(getLoader(), 21789);
    }

    @Override
    public void disable() {
        this.commandManager.unregisterFeatures();
        this.skullManager.disable();
        super.disable();
    }

    @Override
    protected void setupSenderFactory() {
        this.senderFactory = new SparrowBukkitSenderFactory(this);
    }

    @Override
    protected void setupCommandManager() {
        this.commandManager = new SparrowBukkitCommandManager(this);
        this.commandManager.registerDefaultFeatures();
    }

    @Override
    public SparrowBukkitBootstrap getBootstrap() {
        return bootstrap;
    }

    public JavaPlugin getLoader() {
        return this.bootstrap.getLoader();
    }

    public static SparrowBukkitPlugin getInstance() {
        return plugin;
    }

    public SenderFactory<SparrowBukkitPlugin, CommandSender> getSenderFactory() {
        return senderFactory;
    }

    public SparrowCommandManager<CommandSender> getCommandManager() {
        return commandManager;
    }

    public SparrowBukkitSkullManager getSkullManager() {
        return skullManager;
    }

    public SparrowBukkitItemFactory getItemFactory() {
        return itemFactory;
    }
}

package net.momirealms.sparrow.plugin.scheduler;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.AbstractBukkitExecutor;
import net.momirealms.sparrow.plugin.scheduler.executor.BukkitExecutor;
import net.momirealms.sparrow.plugin.scheduler.executor.FoliaExecutor;
import net.momirealms.sparrow.util.VersionHelper;


public final class BukkitSchedulerAdapter extends AbstractJavaScheduler {
    private final SparrowPlugin plugin;
    private final AbstractBukkitExecutor sync;

    public BukkitSchedulerAdapter(SparrowPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        if (VersionHelper.hasFoliaPatch) {
            this.sync = new FoliaExecutor(plugin);
        } else {
            this.sync = new BukkitExecutor(plugin);
        }
    }

    @Override
    public AbstractBukkitExecutor platform() {
        return this.sync;
    }
}

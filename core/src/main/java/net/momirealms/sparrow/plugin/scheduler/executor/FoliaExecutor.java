package net.momirealms.sparrow.plugin.scheduler.executor;

import net.momirealms.sparrow.plugin.scheduler.task.FoliaTask;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.Nullable;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import org.jetbrains.annotations.NotNull;

public final class FoliaExecutor extends AbstractBukkitExecutor {
    private final SparrowPlugin plugin;

    public FoliaExecutor(SparrowPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    @Override
    public void execute(@NotNull Runnable r) {
        Bukkit.getGlobalRegionScheduler().execute(this.plugin.javaPlugin(), r);
    }

    @Override
    public void run(Runnable r, Runnable retired, Entity entity) {
        if (this.runLater(r, retired, 0, entity) == null) {
            retired.run();
        }
    }

    @Override
    public void run(Runnable r, World world, int x, int z) {
        if (world == null) {
            execute(r);
        } else {
            Bukkit.getRegionScheduler().execute(this.plugin.javaPlugin(), world, x, z, r);
        }
    }

    @Override
    public void runDelayed(Runnable r, World world, int x, int z) {
        run(r, world, x, z);
    }

    @Override
    public void runDelayed(Runnable r, Runnable retired, Entity entity) {
        this.run(r, retired, entity);
    }

    @Override
    public SchedulerTask runLater(Runnable r, long delay, World world, int x, int z) {
        if (world == null) {
            if (delay <= 0) {
                return new FoliaTask(Bukkit.getGlobalRegionScheduler().run(this.plugin.javaPlugin(), scheduledTask -> r.run()));
            } else {
                return new FoliaTask(Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin.javaPlugin(), scheduledTask -> r.run(), delay));
            }
        } else {
            if (delay <= 0) {
                return new FoliaTask(Bukkit.getRegionScheduler().run(this.plugin.javaPlugin(), world, x, z, scheduledTask -> r.run()));
            } else {
                return new FoliaTask(Bukkit.getRegionScheduler().runDelayed(this.plugin.javaPlugin(), world, x, z, scheduledTask -> r.run(), delay));
            }
        }
    }

    @Override
    @Nullable
    public SchedulerTask runLater(Runnable r, Runnable retired, long delay, Entity entity) {
        if (delay <= 0) {
            return wrap(entity.getScheduler().run(this.plugin.javaPlugin(), (t) -> r.run(), retired));
        } else {
            return wrap(entity.getScheduler().runDelayed(this.plugin.javaPlugin(), (t) -> r.run(), retired, delay));
        }
    }

    @Override
    public SchedulerTask runRepeating(Runnable r, long delay, long period, World world, int x, int z) {
        if (world == null) {
            return new FoliaTask(Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin.javaPlugin(), scheduledTask -> r.run(), delay, period));
        } else {
            return new FoliaTask(Bukkit.getRegionScheduler().runAtFixedRate(this.plugin.javaPlugin(), world, x, z, scheduledTask -> r.run(), delay, period));
        }
    }

    @Override
    @Nullable
    public SchedulerTask runRepeating(Runnable r, Runnable retired, long delay, long period, Entity entity) {
        return wrap(entity.getScheduler().runAtFixedRate(this.plugin.javaPlugin(), (t) -> r.run(), retired, delay, period));
    }

    @Nullable
    private static SchedulerTask wrap(@Nullable ScheduledTask task) {
        return task == null ? null : new FoliaTask(task);
    }

    @Override
    public SchedulerTask runAsyncLater(Runnable r, long delayTicks) {
        return new FoliaTask(Bukkit.getAsyncScheduler().runDelayed(this.plugin.javaPlugin(), t -> r.run(), delayTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Override
    public SchedulerTask runAsyncRepeating(Runnable r, long delayTicks, long periodTicks) {
        return new FoliaTask(Bukkit.getAsyncScheduler().runAtFixedRate(this.plugin.javaPlugin(), t -> r.run(), delayTicks * 50, periodTicks * 50, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}

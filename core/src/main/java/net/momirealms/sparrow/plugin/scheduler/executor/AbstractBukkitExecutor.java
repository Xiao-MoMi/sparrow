package net.momirealms.sparrow.plugin.scheduler.executor;


import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

public abstract class AbstractBukkitExecutor implements PlatformExecutor {

    public void run(Runnable r, Location location) {
        run(r, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public abstract void run(Runnable r, World world, int x, int z);

    public abstract void run(Runnable r, Runnable retired, Entity entity);

    public void runDelayed(Runnable r, Location location) {
        runDelayed(r, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public abstract void runDelayed(Runnable r, World world, int x, int z);

    public abstract void runDelayed(Runnable r, Runnable retired, Entity entity);

    public SchedulerTask runLater(Runnable r, long delay, Location location) {
        return runLater(r, delay, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public abstract SchedulerTask runLater(Runnable r, long delay, World world, int x, int z);

    public abstract SchedulerTask runLater(Runnable r, Runnable retired, long delay, Entity entity);

    public SchedulerTask runRepeating(Runnable r, long delay, long period, Location location) {
        return runRepeating(r, delay, period, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public abstract SchedulerTask runRepeating(Runnable r, long delay, long period, World world, int x, int z);

    public abstract SchedulerTask runRepeating(Runnable r, Runnable retired, long delay, long period, Entity entity);
}

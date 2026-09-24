package net.momirealms.sparrow.plugin.scheduler.executor;

import net.momirealms.sparrow.plugin.scheduler.task.BukkitTask;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.task.DummyTask;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import org.jetbrains.annotations.NotNull;

public final class BukkitExecutor extends AbstractBukkitExecutor {
    private final SparrowPlugin plugin;

    public BukkitExecutor(SparrowPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Entity entity) {
        return Bukkit.isPrimaryThread() && available(entity);
    }

    @Override
    public void execute(@NotNull Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin.javaPlugin(), r);
    }

    @Override
    public void run(Runnable r, Location location) {
        execute(r);
    }

    @Override
    public void run(Runnable r, World world, int x, int z) {
        execute(r);
    }

    @Override
    public void run(Runnable r, Runnable retired, Entity entity) {
        this.execute(() -> {
            if (available(entity)) {
                r.run();
            } else {
                retired.run();
            }
        });
    }

    @Override
    public void runDelayed(Runnable r, World world, int x, int z) {
        Bukkit.getScheduler().runTask(this.plugin.javaPlugin(), r);
    }

    @Override
    public void runDelayed(Runnable r, Location location) {
        Bukkit.getScheduler().runTask(this.plugin.javaPlugin(), r);
    }

    @Override
    public void runDelayed(Runnable r, Runnable retired, Entity entity) {
        if (this.runLater(r, retired, 0, entity) == null) {
            retired.run();
        }
    }

    @Override
    public SchedulerTask runLater(Runnable r, long delay, World world, int x, int z) {
        return runLater0(r, delay);
    }

    @Override
    @Nullable
    public SchedulerTask runLater(Runnable r, Runnable retired, long delay, Entity entity) {
        if (Bukkit.isPrimaryThread() && !available(entity)) {
            return null;
        }
        return new BukkitTask(Bukkit.getScheduler().runTaskLater(this.plugin.javaPlugin(), () -> {
            if (available(entity)) {
                r.run();
            } else {
                retired.run();
            }
        }, Math.max(0, delay)));
    }

    @Override
    public SchedulerTask runLater(Runnable r, long delay, Location location) {
        return runLater0(r, delay);
    }

    @NotNull
    private SchedulerTask runLater0(Runnable r, long delay) {
        if (delay <= 0) {
            if (Bukkit.isPrimaryThread()) {
                r.run();
                return new DummyTask();
            } else {
                return new BukkitTask(Bukkit.getScheduler().runTask(this.plugin.javaPlugin(), r));
            }
        }
        return new BukkitTask(Bukkit.getScheduler().runTaskLater(this.plugin.javaPlugin(), r, delay));
    }

    @Override
    public SchedulerTask runRepeating(Runnable r, long delay, long period, World world, int x, int z) {
        return new BukkitTask(Bukkit.getScheduler().runTaskTimer(this.plugin.javaPlugin(), r, delay, period));
    }

    @Override
    @Nullable
    public SchedulerTask runRepeating(Runnable r, Runnable retired, long delay, long period, Entity entity) {
        if (Bukkit.isPrimaryThread() && !available(entity)) {
            return null;
        }
        BukkitRunnable repeating = new BukkitRunnable() {
            @Override
            public void run() {
                if (available(entity)) {
                    r.run();
                } else {
                    this.cancel();
                    retired.run();
                }
            }
        };
        return new BukkitTask(repeating.runTaskTimer(this.plugin.javaPlugin(), delay, period));
    }

    private static boolean available(Entity entity) {
        return entity instanceof Player player ? player.isOnline() : entity.isValid();
    }

    @Override
    public SchedulerTask runRepeating(Runnable r, long delay, long period, Location location) {
        return new BukkitTask(Bukkit.getScheduler().runTaskTimer(this.plugin.javaPlugin(), r, delay, period));
    }

    @Override
    public SchedulerTask runAsyncLater(Runnable r, long delayTicks) {
        return new BukkitTask(Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin.javaPlugin(), r, delayTicks));
    }

    @Override
    public SchedulerTask runAsyncRepeating(Runnable r, long delayTicks, long periodTicks) {
        return new BukkitTask(Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin.javaPlugin(), r, delayTicks, periodTicks));
    }
}

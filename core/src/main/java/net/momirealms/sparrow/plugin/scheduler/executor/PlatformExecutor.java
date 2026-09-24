package net.momirealms.sparrow.plugin.scheduler.executor;

import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.bukkit.entity.Entity;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;

public interface PlatformExecutor extends Executor {

    /**
     * 判断当前线程是否拥有实体的执行权.
     *
     * @param entity 目标实体
     * @return 当前线程拥有执行权时返回 true
     */
    boolean isOwnedByCurrentRegion(@NotNull Entity entity);

    // Run

    void run(Runnable r, World world, int x, int z);

    void run(Runnable r, Runnable retired, Entity entity);

    default void run(Runnable r) {
        run(r, null, 0, 0);
    }

    // Delayed

    void runDelayed(Runnable r, World world, int x, int z);

    void runDelayed(Runnable r, Runnable retired, Entity entity);

    default void runDelayed(Runnable r) {
        runDelayed(r, null, 0, 0);
    }

    // Later

    default SchedulerTask runLater(Runnable r, long delay) {
        return runLater(r, delay, null, 0 ,0);
    }

    SchedulerTask runLater(Runnable r, long delay, World world, int x, int z);

    /**
     * 安排实体任务, 非正延迟也会提交到后续调度周期.
     *
     * @param r 实体可用时执行的任务
     * @param retired 等待期间实体退役时执行的回调, <strong>只能释放本地状态</strong>
     * @param delay 延迟 tick 数
     * @param entity 目标实体
     * @return 任务句柄, 实体已退役时返回 null, 此时由调用方处理退役
     */
    @Nullable
    SchedulerTask runLater(Runnable r, Runnable retired, long delay, Entity entity);

    // Repeating

    default SchedulerTask runRepeating(Runnable r, long delay, long period) {
        return runRepeating(r, delay, period, null, 0, 0);
    }

    SchedulerTask runRepeating(Runnable r, long delay, long period, World world, int x, int z);

    /**
     * 重复执行实体任务, 实体退役后停止执行.
     *
     * @param r 每个周期执行的任务
     * @param retired 等待期间实体退役时执行的回调, <strong>只能释放本地状态</strong>
     * @param delay 首次延迟 tick 数
     * @param period 重复间隔 tick 数
     * @param entity 目标实体
     * @return 任务句柄, 实体已退役时返回 null, 此时由调用方处理退役
     */
    @Nullable
    SchedulerTask runRepeating(Runnable r, Runnable retired, long delay, long period, Entity entity);

    SchedulerTask runAsyncLater(Runnable r, long delayTicks);

    SchedulerTask runAsyncRepeating(Runnable r, long delayTicks, long periodTicks);
}

package net.momirealms.sparrow.plugin.scheduler;

import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface SchedulerAdapter {

    /**
     * 返回异步工作执行器.
     *
     * @return 异步工作执行器
     */
    Executor async();

    PlatformExecutor platform();

    /**
     * 在异步工作执行器中执行任务.
     *
     * @param task 要执行的任务
     */
    default void executeAsync(Runnable task) {
        async().execute(task);
    }

    /**
     * 按墙钟时间延迟执行异步任务.
     *
     * @param task 要执行的任务
     * @param delay 延迟时间
     * @param unit 时间单位
     * @return 可取消的调度任务
     */
    SchedulerTask asyncLater(Runnable task, long delay, TimeUnit unit);

    /**
     * 按墙钟时间重复执行异步任务.
     *
     * @param task 要执行的任务
     * @param delay 首次执行前的延迟
     * @param interval 相邻执行之间的间隔
     * @param unit 时间单位
     * @return 可取消的调度任务
     */
    SchedulerTask asyncRepeating(Runnable task, long delay, long interval, TimeUnit unit);

    /**
     * 按墙钟时间重复执行可以取消自身的异步任务.
     *
     * @param task 接收自身任务句柄的回调
     * @param delay 首次执行前的延迟
     * @param interval 相邻执行之间的间隔
     * @param unit 时间单位
     * @return 可取消的调度任务
     */
    SchedulerTask asyncRepeating(Consumer<SchedulerTask> task, long delay, long interval, TimeUnit unit);

    /**
     * 停止墙钟定时器并等待已提交的定时回调退出.
     */
    void shutdownScheduler();

    /**
     * 停止异步工作执行器并等待已提交的任务退出.
     */
    void shutdownExecutor();
}
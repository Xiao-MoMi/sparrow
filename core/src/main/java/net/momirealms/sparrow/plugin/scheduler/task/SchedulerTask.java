package net.momirealms.sparrow.plugin.scheduler.task;

/**
 * 表示一个可以取消并查询取消状态的调度任务.
 */
public interface SchedulerTask {

    /**
     * 取消任务.
     */
    void cancel();

    /**
     * 返回任务是否已经取消.
     *
     * @return 任务已经取消时返回 {@code true}
     */
    boolean cancelled();
}
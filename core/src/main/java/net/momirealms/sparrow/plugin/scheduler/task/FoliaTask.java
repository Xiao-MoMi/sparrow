package net.momirealms.sparrow.plugin.scheduler.task;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class FoliaTask implements SchedulerTask {
    private final ScheduledTask task;

    public FoliaTask(ScheduledTask task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        this.task.cancel();
    }

    @Override
    public boolean cancelled() {
        return this.task.isCancelled();
    }
}

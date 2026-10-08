package net.momirealms.sparrow.plugin.scheduler.task;

import java.util.concurrent.ScheduledFuture;

public final class AsyncTask implements SchedulerTask {
    private final ScheduledFuture<?> future;

    public AsyncTask(ScheduledFuture<?> future) {
        this.future = future;
    }

    @Override
    public void cancel() {
        this.future.cancel(false);
    }

    @Override
    public boolean cancelled() {
        return this.future.isCancelled();
    }
}
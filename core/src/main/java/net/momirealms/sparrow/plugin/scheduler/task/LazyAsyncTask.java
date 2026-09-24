package net.momirealms.sparrow.plugin.scheduler.task;

import java.util.concurrent.ScheduledFuture;

public final class LazyAsyncTask implements SchedulerTask {

    public ScheduledFuture<?> future;

    @Override
    public void cancel() {
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public boolean cancelled() {
        if (future == null) return false;
        return future.isCancelled();
    }
}

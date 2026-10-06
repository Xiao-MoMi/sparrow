package net.momirealms.sparrow.plugin.scheduler.task;

public final class DummyTask implements SchedulerTask {

    @Override
    public void cancel() {
        // This sentinel is always cancelled, so cancellation has no additional effect.
    }

    @Override
    public boolean cancelled() {
        return true;
    }
}

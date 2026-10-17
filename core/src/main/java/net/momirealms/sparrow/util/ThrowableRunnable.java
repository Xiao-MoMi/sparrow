package net.momirealms.sparrow.util;

@FunctionalInterface
public interface ThrowableRunnable {

    void run() throws Throwable;
}

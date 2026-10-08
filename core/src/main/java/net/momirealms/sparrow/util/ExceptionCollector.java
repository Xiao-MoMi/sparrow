package net.momirealms.sparrow.util;

import org.jetbrains.annotations.Nullable;

public final class ExceptionCollector<T extends Throwable> {
    private final Class<T> exceptionClass;
    @Nullable
    private T result;

    /**
     * 创建一个指定异常类型的异常收集器.
     * 收集器会缓存首个异常实例, 后续同类型异常会以 suppressed 异常的形式附加到首个异常上.
     *
     * 泛型参数 `T` 应与 `exceptionClass` 保持一致, 否则会破坏类型语义
     * @param exceptionClass 允许被收集和识别的异常类型
     */
    public ExceptionCollector(Class<T> exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    /**
     * 向收集器中追加一个异常实例.
     * 如果当前尚未记录异常, 该异常会成为主异常.
     * 如果已经存在主异常, 新异常会通过 `addSuppressed` 追加为附加异常.
     *
     * 当 `throwable` 为 `null` 且当前尚无主异常时, 收集结果会保持为 `null`
     * @param throwable 需要收集的异常对象
     */
    public void add(T throwable) {
        if (this.result == null) {
            this.result = throwable;
        } else {
            this.result.addSuppressed(throwable);
        }
    }


    @Nullable
    public T result() {
        return this.result;
    }

    /**
     * 如果当前存在已收集的异常, 则立即抛出该异常.
     * 抛出的异常对象包含此前通过 `add(T)` 聚合的所有 suppressed 异常.
     *
     * @throws T 当收集器中已存在异常时抛出该异常
     */
    public void throwIfPresent() throws T {
        if (this.result != null) {
            throw this.result;
        }
    }

    /**
     * 先收集指定异常, 再立即根据当前状态决定是否抛出.
     * 该方法等价于顺序执行 `add(throwable)` 与 `throwIfPresent()`.
     *
     * @param throwable 需要追加并检查抛出的异常对象
     * @throws T 在追加后收集器存在主异常时抛出该异常
     */
    public void addAndThrow(T throwable) throws T {
        this.add(throwable);
        this.throwIfPresent();
    }

    /**
     * 执行给定任务并捕获异常.
     * 如果任务抛出的异常属于当前收集器声明的异常类型, 则会被收集.
     * 如果抛出的是其他类型异常, 则通过 `ThrowableUtils.sneakyThrow(Throwable)` 原样重新抛出, 不会被吞掉.
     *
     * 该方法不会自动抛出已收集异常, 调用方需要在合适时机显式调用 `throwIfPresent()`
     * @param runnable 需要执行的任务
     */
    public void runCatching(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (this.exceptionClass.isInstance(t)) {
                this.add(this.exceptionClass.cast(t));
            } else {
                ThrowableUtils.sneakyThrow(t);
            }
        }
    }
}
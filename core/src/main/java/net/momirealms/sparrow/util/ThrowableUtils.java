package net.momirealms.sparrow.util;

/**
 * 异常处理工具类, 提供绕过编译器受检异常检查的功能.
 */
public final class ThrowableUtils {
    private ThrowableUtils() {}

    /**
     * 执行一个可能抛出异常的提供者, 并通过 sneakyThrow 将任何捕获的异常作为非受检异常抛出.
     * 
     * @param supplier 可能抛出异常的逻辑代码块
     * @param <T> 返回值类型
     * @return 提供者执行后的返回值
     * @throws RuntimeException 当提供者抛出异常时, 实际抛出的是原始异常
     */
    public static <T> T sneakyThrow(ThrowableSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            throw sneakyThrow(t);
        }
    }

    /**
     * 利用泛型类型擦除机制, 欺骗编译器将受检异常作为非受检异常抛出.
     * 使用时需要注意, 调用此方法后代码执行会中断.
     * 
     * @param t 需要抛出的异常实例
     * @param <E> 泛型异常类型
     * @return 实际上不会返回任何值, 因为总是会抛出异常
     * @throws E 被强制转换并抛出的异常
     */
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> E sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }

    /**
     * 函数式接口, 表示一个不接受参数且返回结果的提供者, 在执行过程中可能抛出异常.
     * 
     * @param <T> 提供者返回的结果类型
     */
    @FunctionalInterface
    public interface ThrowableSupplier<T> {

        /**
         * 获取一个结果.
         * 
         * @return 计算得到的结果
         * @throws Throwable 如果在计算过程中发生错误
         */
        T get() throws Throwable;
    }
}

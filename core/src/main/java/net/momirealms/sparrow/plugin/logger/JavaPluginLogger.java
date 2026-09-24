package net.momirealms.sparrow.plugin.logger;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于 Java 标准日志库 (java.util.logging) 的插件日志记录器实现.
 * 将 PluginLogger 接口的方法映射到 Java Logger 对应的日志级别.
 */
public final class JavaPluginLogger implements PluginLogger {
    private final Logger logger;

    public JavaPluginLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * 输出一条 info 级别的日志消息.
     *
     * @param s 日志消息内容.
     */
    @Override
    public void info(String s) {
        this.logger.info(s);
    }

    /**
     * 输出一条 warn 级别的日志消息.
     *
     * @param s 日志消息内容.
     */
    @Override
    public void warn(String s) {
        this.logger.warning(s);
    }

    /**
     * 输出一条 warn 级别的日志消息, 包含异常堆栈信息.
     *
     * @param s 日志消息内容.
     * @param t 关联的异常对象.
     */
    @Override
    public void warn(String s, Throwable t) {
        this.logger.log(Level.WARNING, s, t);
    }

    /**
     * 输出一条 error 级别的日志消息.
     *
     * @param s 日志消息内容.
     */
    @Override
    public void error(String s) {
        this.logger.severe(s);
    }

    /**
     * 输出一条 error 级别的日志消息, 包含异常堆栈信息.
     *
     * @param s 日志消息内容.
     * @param t 关联的异常对象.
     */
    @Override
    public void error(String s, Throwable t) {
        this.logger.log(Level.SEVERE, s, t);
    }
}
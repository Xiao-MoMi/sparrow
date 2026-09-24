package net.momirealms.sparrow.plugin.logger;

import org.apache.logging.log4j.Logger;

/**
 * 基于 Apache Log4j 2 的插件日志记录器实现.
 * 将 PluginLogger 接口的方法直接委托给 Log4j 的 Logger 实例.
 */
public final class Log4jPluginLogger implements PluginLogger {
    private final Logger logger;

    public Log4jPluginLogger(Logger logger) {
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
        this.logger.warn(s);
    }

    /**
     * 输出一条 warn 级别的日志消息, 包含异常堆栈信息.
     *
     * @param s 日志消息内容.
     * @param t 关联的异常对象.
     */
    @Override
    public void warn(String s, Throwable t) {
        this.logger.warn(s, t);
    }

    /**
     * 输出一条 error 级别的日志消息.
     *
     * @param s 日志消息内容.
     */
    @Override
    public void error(String s) {
        this.logger.error(s);
    }

    /**
     * 输出一条 error 级别的日志消息, 包含异常堆栈信息.
     *
     * @param s 日志消息内容.
     * @param t 关联的异常对象.
     */
    @Override
    public void error(String s, Throwable t) {
        this.logger.error(s, t);
    }
}
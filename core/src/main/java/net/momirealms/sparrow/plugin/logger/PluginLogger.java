package net.momirealms.sparrow.plugin.logger;

import java.io.File;
import java.nio.file.Path;

/**
 * 插件日志记录器接口.
 * 提供不同级别的日志输出方法, 包括 info, warn 和 error.
 */
public interface PluginLogger {

    /**
     * 输出一条 info 级别的日志消息.
     *
     * @param s 日志消息内容.
     */
    void info(String s);

    /**
     * 输出一条 warn 级别的日志消息.
     *
     * @param s 日志消息内容.
     */
    void warn(String s);

    /**
     * 输出一条 warn 级别的日志消息, 包含关联的文件信息.
     *
     * @param file 与警告相关的文件对象.
     * @param s    日志消息内容.
     */
    default void warn(File file, String s) {
        warn("Error in file: " + file.getAbsolutePath() + " - " + s);
    }

    /**
     * 输出一条 warn 级别的日志消息, 包含关联的文件路径信息.
     *
     * @param file 与警告相关的文件路径.
     * @param s    日志消息内容.
     */
    default void warn(Path file, String s) {
        warn("Error in file: " + file.toAbsolutePath() + " - " + s);
    }

    /**
     * 输出一条 warn 级别的日志消息, 包含关联的文件路径信息和异常堆栈.
     *
     * @param file 与警告相关的文件路径.
     * @param s    日志消息内容.
     * @param t    关联的异常对象.
     */
    default void warn(Path file, String s, Throwable t) {
        warn("Error in file: " + file.toAbsolutePath() + " - " + s, t);
    }

    /**
     * 输出一条 warn 级别的日志消息, 包含异常堆栈信息.
     *
     * @param s 日志消息内容.
     * @param t 关联的异常对象.
     */
    void warn(String s, Throwable t);

    /**
     * 输出一条 error 级别的日志消息.
     *
     * @param s 日志消息内容.
     */
    void error(String s);

    /**
     * 输出一条 error 级别的日志消息, 包含异常堆栈信息.
     *
     * @param s 日志消息内容.
     * @param t 关联的异常对象.
     */
    void error(String s, Throwable t);
}
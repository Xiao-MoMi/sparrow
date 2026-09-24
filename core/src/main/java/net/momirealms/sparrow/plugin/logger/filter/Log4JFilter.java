package net.momirealms.sparrow.plugin.logger.filter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.message.Message;

public interface Log4JFilter {

    /**
     * 基于 LogEvent 进行日志过滤.
     *
     * @param event 需要过滤的日志事件.
     * @return 过滤结果 (DENY, NEUTRAL 或 ACCEPT).
     */
    Filter.Result filter(LogEvent event);

    /**
     * 基于 Logger, Level, Marker, Message 和 Throwable 进行日志过滤.
     *
     * @param logger 产生日志的 Logger 实例.
     * @param level  日志级别.
     * @param marker 日志标记.
     * @param msg    日志消息对象.
     * @param t      关联的异常对象.
     * @return 过滤结果.
     */
    Filter.Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t);

    /**
     * 基于 Logger, Level, Marker, 字符串消息和参数进行日志过滤.
     *
     * @param logger 产生日志的 Logger 实例.
     * @param level  日志级别.
     * @param marker 日志标记.
     * @param msg    日志消息字符串.
     * @param params 消息格式化参数.
     * @return 过滤结果.
     */
    Filter.Result filter(Logger logger, Level level, Marker marker, String msg, Object... params);

    /**
     * 基于 Logger, Level, Marker, Object 消息和 Throwable 进行日志过滤.
     *
     * @param logger 产生日志的 Logger 实例.
     * @param level  日志级别.
     * @param marker 日志标记.
     * @param msg    日志消息对象.
     * @param t      关联的异常对象.
     * @return 过滤结果.
     */
    Filter.Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t);
}
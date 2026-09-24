package net.momirealms.sparrow.plugin.logger.filter;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.impl.MutableLogEvent;

@Plugin(name = "DisconnectLogFilter", category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE)
public final class DisconnectLogFilter extends AbstractFilter {
    private static final String TARGET_LOGGER = "net.minecraft.server.network.ServerConfigurationPacketListenerImpl";
    private static final String TARGET_MESSAGE_PATTERN = "{} lost connection: {}";
    private static DisconnectLogFilter instance;
    private boolean enable = false;

    public DisconnectLogFilter() {
        instance = this;
    }

    /**
     * 获取该过滤器的单例实例.
     *
     * @return DisconnectLogFilter 的单例实例.
     */
    public static DisconnectLogFilter instance() {
        return instance;
    }

    /**
     * 设置是否启用该过滤器.
     *
     * @param enable true 表示启用过滤, false 表示禁用过滤.
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 对日志事件进行过滤判断.
     *
     * @param event 需要过滤的日志事件.
     * @return 过滤结果.
     */
    @Override
    public Result filter(LogEvent event) {
        // 如果过滤器未启用, 直接返回 NEUTRAL (不干预).
        if (!enable) {
            return Result.NEUTRAL;
        }
        // 如果日志事件的记录器名称不是目标记录器, 返回 NEUTRAL.
        if (!event.getLoggerName().equals(TARGET_LOGGER)) {
            return Result.NEUTRAL;
        }
        // 如果日志消息是 MutableLogEvent 且其格式匹配目标断开连接消息模式, 返回 DENY (拒绝).
        if (event.getMessage() instanceof MutableLogEvent msg) {
            String format = msg.getFormat();

            if (TARGET_MESSAGE_PATTERN.equals(format)) {
                return Result.DENY;
            }
        }
        return Result.NEUTRAL;
    }
}
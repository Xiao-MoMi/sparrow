package net.momirealms.sparrow.plugin.logger;

import net.momirealms.sparrow.plugin.SparrowPlugin;

import java.util.function.Supplier;

public enum Debugger {
    COMMON(() -> false);

    private final Supplier<Boolean> condition;

    Debugger(Supplier<Boolean> condition) {
        this.condition = condition;
    }

    /**
     * 输出一条 debug 级别的日志消息.
     *
     * @param message 日志消息的延迟提供者, 只有在条件满足时才会被调用.
     */
    public void debug(Supplier<String> message) {
        if (this.condition.get()) {
            String s = message.get();
            if (s != null) {
                SparrowPlugin.instance().logger().info("[DEBUG] " + s);
            }
        }
    }

    /**
     * 输出一条 debug 级别的警告日志消息, 可附带异常信息.
     *
     * @param message 日志消息的延迟提供者, 只有在条件满足时才会被调用.
     * @param e       关联的异常对象, 可为 null.
     */
    public void warn(Supplier<String> message, Throwable e) {
        if (this.condition.get()) {
            String str = message.get();
            if (str == null) return;
            if (e != null) {
                SparrowPlugin.instance().logger().warn("[DEBUG] " + str, e);
            } else {
                SparrowPlugin.instance().logger().warn("[DEBUG] " + str);
            }
        }
    }
}
package net.momirealms.sparrow.proxy.common.logger;

public interface ProxyLogger {

    void info(String message);

    void warn(String message, Throwable cause);

    void error(String message, Throwable cause);
}

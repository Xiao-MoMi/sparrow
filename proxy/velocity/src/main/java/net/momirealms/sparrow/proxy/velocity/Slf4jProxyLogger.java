package net.momirealms.sparrow.proxy.velocity;

import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;
import org.slf4j.Logger;

record Slf4jProxyLogger(Logger logger) implements ProxyLogger {

    @Override
    public void info(String message) {
        this.logger.info(message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        this.logger.warn(message, cause);
    }

    @Override
    public void error(String message, Throwable cause) {
        this.logger.error(message, cause);
    }
}

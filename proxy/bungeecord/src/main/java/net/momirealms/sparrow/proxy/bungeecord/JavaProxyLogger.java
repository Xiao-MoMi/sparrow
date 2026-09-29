package net.momirealms.sparrow.proxy.bungeecord;

import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

record JavaProxyLogger(Logger logger) implements ProxyLogger {

    @Override
    public void info(String message) {
        this.logger.info(message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        this.logger.log(Level.WARNING, message, cause);
    }

    @Override
    public void error(String message, Throwable cause) {
        this.logger.log(Level.SEVERE, message, cause);
    }
}

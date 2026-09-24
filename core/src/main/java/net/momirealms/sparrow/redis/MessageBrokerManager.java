package net.momirealms.sparrow.redis;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.redis.messagebroker.Logger;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public final class MessageBrokerManager {
    private final SparrowPlugin plugin;
    private volatile MessageBroker<ByteBuf> broker;

    public MessageBrokerManager(@NotNull SparrowPlugin plugin) {
        this.plugin = plugin;
    }

    public void onLoad() {
        RedisConnector connector = this.plugin.redisConnector();
        // Redis Pub/Sub 跨数据库共享频道, 用数据库编号隔离各组服务器的消息.
        this.broker = MessageBroker.builder(buffer -> buffer)
                .channel(("sparrow:db:" + connector.database() + ":messages").getBytes(StandardCharsets.UTF_8))
                .serverId(PluginConfig.redis().serverId())
                .logger(new BrokerLogger(this.plugin.logger()))
                .connection(connector.brokerConnection())
                .build();
        this.broker.subscribe();
    }

    @NotNull
    public MessageBroker<ByteBuf> broker() {
        return this.broker;
    }

    public void onDisable() {
        MessageBroker<ByteBuf> broker = this.broker;
        this.broker = null;
        if (broker != null) broker.unsubscribe();
    }

    private record BrokerLogger(PluginLogger logger) implements Logger {
        @Override
        public void error(String message, Throwable cause) {
            this.logger.error(message, cause);
        }

        @Override
        public void warn(String message, Throwable cause) {
            this.logger.warn(message, cause);
        }

        @Override
        public void info(String message) {
            this.logger.info(message);
        }

        @Override
        public void debug(String message) {
        }
    }
}

package net.momirealms.sparrow.proxy.common.redis;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.proxy.common.ProxyPlatform;
import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;
import net.momirealms.sparrow.proxy.common.message.DisconnectMessage;
import net.momirealms.sparrow.redis.messagebroker.Logger;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public final class MessageBrokerManager {
    private static final String SERVER_ID = "proxy";

    private final MessageBroker<ByteBuf> broker;

    public MessageBrokerManager(@NotNull ProxyPlatform platform, @NotNull RedisConnector connector) {
        // 代理频道只放和后端往来的消息, 注册顺序需要和后端代理频道的注册顺序保持一致.
        this.broker = MessageBroker.builder(buffer -> buffer)
                .channel(("sparrow:db:" + connector.database() + ":proxy").getBytes(StandardCharsets.UTF_8))
                .serverId(SERVER_ID)
                .logger(new BrokerLogger(platform.logger()))
                .connection(connector.brokerConnection())
                .build();
        this.broker.registry().register(DisconnectMessage.ID, DisconnectMessage.codec(platform));
    }

    public void subscribe() {
        this.broker.subscribe();
    }

    public void unsubscribe() {
        this.broker.unsubscribe();
    }

    @NotNull
    public MessageBroker<ByteBuf> broker() {
        return this.broker;
    }

    private record BrokerLogger(ProxyLogger logger) implements Logger {
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

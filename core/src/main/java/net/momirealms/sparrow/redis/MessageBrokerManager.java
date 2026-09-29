package net.momirealms.sparrow.redis;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.feature.ban.BanMessage;
import net.momirealms.sparrow.player.KickMessage;
import net.momirealms.sparrow.player.cluster.PlayerPresenceMessage;
import net.momirealms.sparrow.player.teleport.TeleportRequest;
import net.momirealms.sparrow.player.teleport.TeleportResponse;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.redis.heartbeat.ServerProbeMessage;
import net.momirealms.sparrow.redis.heartbeat.ServerProbeResponseMessage;
import net.momirealms.sparrow.redis.messagebroker.Logger;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.proxy.DisconnectMessage;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

public final class MessageBrokerManager {
    private final SparrowPlugin plugin;
    private volatile MessageBroker<ByteBuf> broker;
    private volatile MessageBroker<ByteBuf> proxyBroker;

    public MessageBrokerManager(@NotNull SparrowPlugin plugin) {
        this.plugin = plugin;
    }

    public void onLoad() {
        RedisConnector connector = this.plugin.redisConnector();
        // Redis Pub/Sub 跨数据库后端共享频道.
        this.broker = MessageBroker.builder(buffer -> buffer)
                .channel(("sparrow:db:" + connector.database() + ":messages").getBytes(StandardCharsets.UTF_8))
                .serverId(ServerConfig.serverId())
                .logger(new BrokerLogger(this.plugin.logger()))
                .connection(connector.brokerConnection())
                .build();
        this.broker.registry().register(ServerProbeMessage.ID, ServerProbeMessage.CODEC);
        this.broker.registry().register(ServerProbeResponseMessage.ID, ServerProbeResponseMessage.CODEC);
        this.broker.registry().register(PlayerPresenceMessage.ID, PlayerPresenceMessage.CODEC);
        this.broker.registry().register(TeleportRequest.ID, TeleportRequest.CODEC);
        this.broker.registry().register(TeleportResponse.ID, TeleportResponse.CODEC);
        this.broker.registry().register(KickMessage.ID, KickMessage.CODEC);
        this.broker.registry().register(BanMessage.ID, BanMessage.CODEC);
        this.broker.subscribe();
        // Redis Pub/Sub Proxy 代理频道.
        this.proxyBroker = MessageBroker.builder(buffer -> buffer)
                .channel(("sparrow:db:" + connector.database() + ":proxy").getBytes(StandardCharsets.UTF_8))
                .serverId(ServerConfig.serverId())
                .logger(new BrokerLogger(this.plugin.logger()))
                .connection(connector.brokerConnection())
                .build();
        this.proxyBroker.registry().register(DisconnectMessage.ID, DisconnectMessage.CODEC);
    }

    @NotNull
    public MessageBroker<ByteBuf> broker() {
        return this.broker;
    }

    @NotNull
    public MessageBroker<ByteBuf> proxyBroker() {
        return this.proxyBroker;
    }

    public void onDisable() {
        MessageBroker<ByteBuf> broker = this.broker;
        this.broker = null;
        if (broker != null) broker.unsubscribe();
        MessageBroker<ByteBuf> proxyBroker = this.proxyBroker;
        this.proxyBroker = null;
        if (proxyBroker != null) proxyBroker.unsubscribe();
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

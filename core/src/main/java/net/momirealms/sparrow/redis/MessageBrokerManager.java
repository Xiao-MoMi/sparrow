package net.momirealms.sparrow.redis;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.feature.ban.BanMessage;
import net.momirealms.sparrow.feature.mute.MuteMessage;
import net.momirealms.sparrow.feature.home.HomeChangedMessage;
import net.momirealms.sparrow.feature.warp.WarpMessage;
import net.momirealms.sparrow.feature.spawn.SpawnMessage;
import net.momirealms.sparrow.redis.message.player.BroadcastMessage;
import net.momirealms.sparrow.redis.proxy.PlayerPresenceMessage;
import net.momirealms.sparrow.redis.proxy.PlayerDirectoryResetMessage;
import net.momirealms.sparrow.redis.proxy.DisconnectRequest;
import net.momirealms.sparrow.redis.proxy.DisconnectResponse;
import net.momirealms.sparrow.redis.message.teleport.TeleportRequest;
import net.momirealms.sparrow.redis.message.teleport.TeleportResponse;
import net.momirealms.sparrow.redis.message.server.ServerChangedMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.redis.messagebroker.Logger;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.proxy.DisconnectMessage;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public final class MessageBrokerManager {
    private final SparrowPlugin plugin;
    private volatile MessageBroker<FriendlyByteBuf> broker;
    private volatile MessageBroker<FriendlyByteBuf> proxyBroker;

    public MessageBrokerManager(@NotNull SparrowPlugin plugin) {
        this.plugin = plugin;
    }

    public void onLoad() {
        RedisConnector connector = this.plugin.redisConnector();
        // Redis Pub/Sub 跨数据库后端共享频道.
        this.broker = MessageBroker.builder(FriendlyByteBuf::new)
                .channel(("sparrow:db:" + connector.database() + ":messages").getBytes(StandardCharsets.UTF_8))
                .serverId(ServerConfig.serverId())
                .logger(new BrokerLogger(this.plugin.logger()))
                .connection(connector.brokerConnection())
                .build();
        this.broker.registry().register(ServerChangedMessage.ID, ServerChangedMessage.CODEC);
        this.broker.registry().register(TeleportRequest.ID, TeleportRequest.CODEC);
        this.broker.registry().register(TeleportResponse.ID, TeleportResponse.CODEC);
        this.broker.registry().register(BroadcastMessage.ID, BroadcastMessage.CODEC);
        this.broker.registry().register(BanMessage.ID, BanMessage.CODEC);
        this.broker.registry().register(MuteMessage.ID, MuteMessage.CODEC);
        this.broker.registry().register(WarpMessage.ID, WarpMessage.CODEC);
        this.broker.registry().register(SpawnMessage.ID, SpawnMessage.CODEC);
        this.broker.registry().register(HomeChangedMessage.ID, HomeChangedMessage.CODEC);
        // Redis Pub/Sub Proxy 代理频道.
        this.proxyBroker = MessageBroker.builder(FriendlyByteBuf::new)
                .channel(("sparrow:db:" + connector.database() + ":proxy").getBytes(StandardCharsets.UTF_8))
                .serverId(ServerConfig.serverId())
                .logger(new BrokerLogger(this.plugin.logger()))
                .connection(connector.brokerConnection())
                .build();
        this.proxyBroker.registry().register(DisconnectMessage.ID, DisconnectMessage.CODEC);
        this.proxyBroker.registry().register(DisconnectRequest.ID, DisconnectRequest.CODEC);
        this.proxyBroker.registry().register(DisconnectResponse.ID, DisconnectResponse.CODEC);
        this.proxyBroker.registry().register(PlayerPresenceMessage.ID, PlayerPresenceMessage.CODEC);
        this.proxyBroker.registry().register(PlayerDirectoryResetMessage.ID, PlayerDirectoryResetMessage.CODEC);
    }

    public void onEnable() {
        this.broker.subscribe();
    }

    @NotNull
    public MessageBroker<FriendlyByteBuf> broker() {
        return this.broker;
    }

    @NotNull
    public CompletableFuture<Long> publishOneWay(@NotNull OneWayMessage<FriendlyByteBuf> message, @NotNull String targetServer) {
        MessageBroker<FriendlyByteBuf> broker = this.broker();
        message.setTargetServer(targetServer);
        return this.plugin.redisConnector().connection().async().publish(broker.channel(), broker.encode(message)).toCompletableFuture();
    }

    @NotNull
    public MessageBroker<FriendlyByteBuf> proxyBroker() {
        return this.proxyBroker;
    }

    public void onDisable() {
        MessageBroker<FriendlyByteBuf> broker = this.broker;
        this.broker = null;
        if (broker != null) broker.unsubscribe();
        MessageBroker<FriendlyByteBuf> proxyBroker = this.proxyBroker;
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
package net.momirealms.sparrow.redis;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.data.message.DataChangedMessage;
import net.momirealms.sparrow.player.message.BroadcastMessage;
import net.momirealms.sparrow.player.message.ClearTitleMessage;
import net.momirealms.sparrow.player.message.HideBossBarMessage;
import net.momirealms.sparrow.player.message.KickPlayerMessage;
import net.momirealms.sparrow.player.message.PlaySoundMessage;
import net.momirealms.sparrow.player.message.PlayerChangedMessage;
import net.momirealms.sparrow.player.message.PlayerRegistryResetMessage;
import net.momirealms.sparrow.player.message.SendMessageMessage;
import net.momirealms.sparrow.player.message.SendTitleMessage;
import net.momirealms.sparrow.player.message.SendToastMessage;
import net.momirealms.sparrow.player.message.SendTotemAnimationMessage;
import net.momirealms.sparrow.player.message.ShowBossBarMessage;
import net.momirealms.sparrow.player.message.UpdateBossBarProgressMessage;
import net.momirealms.sparrow.player.message.UpdateBossBarTitleMessage;
import net.momirealms.sparrow.player.message.proxy.*;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.server.message.ServerChangedMessage;
import net.momirealms.sparrow.server.message.ServerHeartbeatMessage;
import net.momirealms.sparrow.server.message.ServerProbeRequest;
import net.momirealms.sparrow.server.message.ServerProbeResponse;
import net.momirealms.sparrow.redis.messagebroker.Logger;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.teleport.message.*;
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
                .tags(ServerConfig.tags())
                .logger(new BrokerLogger(this.plugin.logger()))
                .connection(connector.brokerConnection())
                .build();
        this.broker.registry().register(ServerChangedMessage.ID, ServerChangedMessage.CODEC);
        this.broker.registry().register(ServerHeartbeatMessage.ID, ServerHeartbeatMessage.CODEC);
        this.broker.registry().register(ServerProbeRequest.ID, ServerProbeRequest.CODEC);
        this.broker.registry().register(ServerProbeResponse.ID, ServerProbeResponse.CODEC);
        this.broker.registry().register(PlayerChangedMessage.ID, PlayerChangedMessage.CODEC);
        this.broker.registry().register(PlayerRegistryResetMessage.ID, PlayerRegistryResetMessage.CODEC);
        this.broker.registry().register(TeleportPrepareRequest.ID, TeleportPrepareRequest.CODEC);
        this.broker.registry().register(TeleportPrepareResponse.ID, TeleportPrepareResponse.CODEC);
        this.broker.registry().register(TeleportExecuteRequest.ID, TeleportExecuteRequest.CODEC);
        this.broker.registry().register(TeleportExecuteResponse.ID, TeleportExecuteResponse.CODEC);
        this.broker.registry().register(SendMessageMessage.ID, SendMessageMessage.CODEC);
        this.broker.registry().register(SendTitleMessage.ID, SendTitleMessage.CODEC);
        this.broker.registry().register(ClearTitleMessage.ID, ClearTitleMessage.CODEC);
        this.broker.registry().register(KickPlayerMessage.ID, KickPlayerMessage.CODEC);
        this.broker.registry().register(ShowBossBarMessage.ID, ShowBossBarMessage.CODEC);
        this.broker.registry().register(UpdateBossBarProgressMessage.ID, UpdateBossBarProgressMessage.CODEC);
        this.broker.registry().register(UpdateBossBarTitleMessage.ID, UpdateBossBarTitleMessage.CODEC);
        this.broker.registry().register(HideBossBarMessage.ID, HideBossBarMessage.CODEC);
        this.broker.registry().register(PlaySoundMessage.ID, PlaySoundMessage.CODEC);
        this.broker.registry().register(SendToastMessage.ID, SendToastMessage.CODEC);
        this.broker.registry().register(SendTotemAnimationMessage.ID, SendTotemAnimationMessage.CODEC);
        this.broker.registry().register(BroadcastMessage.ID, BroadcastMessage.CODEC);
        this.broker.registry().register(DataChangedMessage.ID, DataChangedMessage.CODEC);
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
        this.proxyBroker.registry().register(ConnectRequest.ID, ConnectRequest.CODEC);
        this.proxyBroker.registry().register(ConnectResponse.ID, ConnectResponse.CODEC);
    }

    public void subscribe() {
        this.broker.subscribe();
        this.proxyBroker.subscribe();
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
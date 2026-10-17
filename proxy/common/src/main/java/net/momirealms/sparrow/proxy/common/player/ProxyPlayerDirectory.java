package net.momirealms.sparrow.proxy.common.player;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.momirealms.sparrow.proxy.common.SparrowProxy;
import net.momirealms.sparrow.proxy.common.message.PlayerDirectoryResetMessage;
import net.momirealms.sparrow.proxy.common.message.PlayerPresenceMessage;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProxyPlayerDirectory {
    private static final byte[] ROSTER_KEY = "sparrow:proxy:players".getBytes(StandardCharsets.UTF_8);

    private final SparrowProxy plugin;
    private final Map<UUID, PlayerPresence> players = new HashMap<>();
    private boolean closed = true;

    public ProxyPlayerDirectory(@NotNull SparrowProxy plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() {
        RedisAsyncCommands<byte[], byte[]> commands = this.plugin.redisConnector().connection().async();
        List<CompletableFuture<?>> writes = new ArrayList<>();
        writes.add(commands.del(ROSTER_KEY).toCompletableFuture());
        for (PlayerPresence player : this.plugin.playerManager().getPlayers()) {
            this.players.put(player.uuid(), player);
            PlayerPresenceMessage message = new PlayerPresenceMessage(player.uuid(), player.name(), player.server(), player.locale());
            writes.add(commands.hset(ROSTER_KEY, key(player.uuid()), encode(message)).toCompletableFuture());
        }
        writes.add(this.publish(new PlayerDirectoryResetMessage()));
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
        this.closed = false;
    }

    public synchronized void connected(@NotNull UUID uuid) {
        if (this.closed) return;
        PlayerPresence player = this.plugin.playerManager().findPlayer(uuid);
        if (player == null) return;
        this.update(player);
    }

    public synchronized void locale(@NotNull UUID uuid, @NotNull Locale locale) {
        if (this.closed) return;
        PlayerPresence player = this.players.get(uuid);
        if (player == null || player.locale().equals(locale)) return;
        this.update(new PlayerPresence(uuid, player.name(), player.server(), locale));
    }

    private void update(PlayerPresence player) {
        if (player.equals(this.players.put(player.uuid(), player))) return;
        PlayerPresenceMessage message = new PlayerPresenceMessage(player.uuid(), player.name(), player.server(), player.locale());
        CompletableFuture<Boolean> write = this.plugin.redisConnector().connection().async()
                .hset(ROSTER_KEY, key(player.uuid()), encode(message))
                .toCompletableFuture();
        this.report(CompletableFuture.allOf(write, this.publish(message)));
    }

    public synchronized void disconnected(@NotNull UUID uuid) {
        if (this.closed || this.players.remove(uuid) == null) return;
        CompletableFuture<Long> write = this.plugin.redisConnector().connection().async()
                .hdel(ROSTER_KEY, key(uuid))
                .toCompletableFuture();
        this.report(CompletableFuture.allOf(write, this.publish(new PlayerPresenceMessage(uuid))));
    }

    // 写入与通知在同一连接中提交, 并由目录锁维持玩家变更的顺序.
    private CompletableFuture<Long> publish(RedisMessage<ByteBuf> message) {
        MessageBroker<ByteBuf> broker = this.plugin.messageBrokerManager().broker();
        return this.plugin.redisConnector().connection().async().publish(broker.channel(), broker.encode(message)).toCompletableFuture();
    }

    private void report(CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, error) -> {
            if (error != null) {
                this.plugin.platform().logger().warn("Failed to publish player directory change", error);
            }
        });
    }

    public void shutdown() {
        CompletableFuture<Void> removal;
        synchronized (this) {
            if (this.closed) return;
            this.closed = true;
            this.players.clear();
            CompletableFuture<Long> deleted = this.plugin.redisConnector().connection().async().del(ROSTER_KEY).toCompletableFuture();
            removal = CompletableFuture.allOf(deleted, this.publish(new PlayerDirectoryResetMessage()));
        }
        removal.join();
    }

    private static byte[] key(UUID uuid) {
        // 16 字节大端序, 高 64 位在前, 低 64 位在后.
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    private static byte[] encode(PlayerPresenceMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            message.writeOnlineRecord(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }
}

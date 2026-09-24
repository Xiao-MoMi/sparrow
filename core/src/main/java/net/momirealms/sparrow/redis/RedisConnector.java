package net.momirealms.sparrow.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.redis.messagebroker.connection.PubSubRedisConnection;
import net.momirealms.sparrow.redis.messagebroker.connection.RedisConnection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class RedisConnector implements AutoCloseable {
    private final PluginConfig.RedisOptions options;
    private RedisClient client;
    private StatefulRedisConnection<byte[], byte[]> connection;
    private PubSubRedisConnection brokerConnection;
    private int database;

    public RedisConnector(@NotNull PluginConfig.RedisOptions options) {
        this.options = options;
    }

    public void initialize() {
        RedisURI uri = RedisURI.create(this.options.url());
        if (!this.options.password().isEmpty()) {
            String username = this.options.username().isEmpty() ? null : this.options.username();
            uri.setCredentialsProvider(RedisCredentialsProvider.from(() -> RedisCredentials.just(username, this.options.password())));
        }
        RedisClient connectedClient = RedisClient.create(uri);
        try {
            StatefulRedisConnection<byte[], byte[]> connected = connectedClient.connect(ByteArrayCodec.INSTANCE);
            try {
                connected.sync().ping();
                this.brokerConnection = new PubSubRedisConnection(connectedClient);
                this.database = uri.getDatabase();
                this.client = connectedClient;
                this.connection = connected;
            } catch (RuntimeException exception) {
                connected.close();
                throw exception;
            }
        } catch (RuntimeException exception) {
            connectedClient.shutdown(0, 2, TimeUnit.SECONDS);
            throw exception;
        }
    }

    /**
     * 返回以字节数组收发数据的 Redis 连接。同步命令会阻塞当前线程。
     *
     * @return 已建立的 Redis 连接
     * @throws IllegalStateException 连接尚未建立时
     */
    @NotNull
    public StatefulRedisConnection<byte[], byte[]> connection() {
        if (this.connection == null) throw new IllegalStateException("Redis is not initialized");
        return this.connection;
    }

    @NotNull
    public RedisClient client() {
        if (this.client == null) throw new IllegalStateException("Redis is not initialized");
        return this.client;
    }

    public int database() {
        return this.database;
    }

    @NotNull
    public RedisConnection brokerConnection() {
        return this.brokerConnection;
    }

    public boolean available() {
        return this.connection != null && this.connection.isOpen();
    }

    @Override
    public void close() {
        if (this.brokerConnection != null) this.brokerConnection.close();
        if (this.connection != null) this.connection.close();
        if (this.client != null) this.client.shutdown(0, 2, TimeUnit.SECONDS);
    }
}

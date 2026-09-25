package net.momirealms.sparrow.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
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
    private static final RedisServerVersion MINIMUM_SERVER_VERSION = new RedisServerVersion(7, 2, 4);

    private final PluginConfig.RedisOptions options;
    private final PluginLogger logger;
    private RedisClient client;
    private StatefulRedisConnection<byte[], byte[]> connection;
    private PubSubRedisConnection brokerConnection;
    private int database;

    public RedisConnector(@NotNull PluginConfig.RedisOptions options, @NotNull PluginLogger logger) {
        this.options = options;
        this.logger = logger;
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
                this.verifyRedisVersion(connected);
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

    private void verifyRedisVersion(StatefulRedisConnection<byte[], byte[]> connection) {
        RedisServerVersion reported;
        try {
            reported = RedisServerVersion.parse(connection.sync().info("server"));
        } catch (RedisException exception) {
            this.logger.warn(TranslationManager.console(LogConstants.REDIS_VERSION_CHECK_FAILED), exception);
            return;
        }
        if (reported == null) {
            this.logger.warn(TranslationManager.console(LogConstants.REDIS_VERSION_CHECK_FAILED));
            return;
        }
        if (reported.atLeast(MINIMUM_SERVER_VERSION)) return;
        this.logger.error(TranslationManager.console(LogConstants.REDIS_VERSION_UNSUPPORTED, reported.toString(), MINIMUM_SERVER_VERSION.toString()));
        throw new IllegalStateException("Redis server version " + reported + " is not supported, Redis " + MINIMUM_SERVER_VERSION + " or later is required");
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

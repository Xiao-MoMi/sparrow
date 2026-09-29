package net.momirealms.sparrow.proxy.common;

import net.momirealms.sparrow.proxy.common.config.ProxyConfig;
import net.momirealms.sparrow.proxy.common.redis.MessageBrokerManager;
import net.momirealms.sparrow.proxy.common.redis.RedisConnector;
import org.jetbrains.annotations.NotNull;

public final class SparrowProxy {
    private final ProxyPlatform platform;
    private RedisConnector redisConnector;
    private MessageBrokerManager messageBrokerManager;

    public SparrowProxy(@NotNull ProxyPlatform platform) {
        this.platform = platform;
    }

    public void enable() {
        ProxyConfig config = ProxyConfig.load(this.platform.dataFolderPath());
        this.redisConnector = new RedisConnector(config.redis());
        this.redisConnector.initialize();
        this.messageBrokerManager = new MessageBrokerManager(this.platform, this.redisConnector);
        this.messageBrokerManager.subscribe();
        this.platform.logger().info("Connected to Redis");
    }

    public void disable() {
        if (this.messageBrokerManager != null) this.messageBrokerManager.unsubscribe();
        if (this.redisConnector != null) this.redisConnector.close();
    }
}

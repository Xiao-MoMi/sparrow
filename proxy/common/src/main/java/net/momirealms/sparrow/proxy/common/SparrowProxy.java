package net.momirealms.sparrow.proxy.common;

import net.momirealms.sparrow.proxy.common.config.ProxyConfig;
import net.momirealms.sparrow.proxy.common.redis.MessageBrokerManager;
import net.momirealms.sparrow.proxy.common.redis.RedisConnector;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerDirectory;
import net.momirealms.sparrow.proxy.common.player.ProxyPlayerManager;
import org.jetbrains.annotations.NotNull;

public final class SparrowProxy {
    private static SparrowProxy instance;

    private final ProxyPlatform platform;
    private final ProxyPlayerManager playerManager;
    private RedisConnector redisConnector;
    private MessageBrokerManager messageBrokerManager;
    private final ProxyPlayerDirectory playerDirectory = new ProxyPlayerDirectory(this);

    public SparrowProxy(@NotNull ProxyPlatform platform, @NotNull ProxyPlayerManager playerManager) {
        this.platform = platform;
        this.playerManager = playerManager;
        instance = this;
    }

    @NotNull
    public static SparrowProxy instance() {
        return instance;
    }

    public void enable() {
        ProxyConfig config = ProxyConfig.load(this.platform.dataFolderPath());
        this.redisConnector = new RedisConnector(config.redis());
        this.redisConnector.initialize();
        this.messageBrokerManager = new MessageBrokerManager(this);
        this.playerManager.enable(this.playerDirectory);
        this.playerDirectory.initialize();
        this.messageBrokerManager.subscribe();
        this.platform.logger().info("Connected to Redis");
    }

    public void disable() {
        this.playerManager.disable();
        this.playerDirectory.shutdown();
        if (this.messageBrokerManager != null) this.messageBrokerManager.unsubscribe();
        if (this.redisConnector != null) this.redisConnector.close();
    }

    @NotNull
    public ProxyPlatform platform() {
        return this.platform;
    }

    @NotNull
    public ProxyPlayerManager playerManager() {
        return this.playerManager;
    }

    @NotNull
    public RedisConnector redisConnector() {
        return this.redisConnector;
    }

    @NotNull
    public MessageBrokerManager messageBrokerManager() {
        return this.messageBrokerManager;
    }

    @NotNull
    public ProxyPlayerDirectory playerDirectory() {
        return this.playerDirectory;
    }
}
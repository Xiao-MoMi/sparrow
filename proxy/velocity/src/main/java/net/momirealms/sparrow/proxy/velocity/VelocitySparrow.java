package net.momirealms.sparrow.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import net.momirealms.sparrow.proxy.common.BuildInfo;
import net.momirealms.sparrow.proxy.common.config.ProxyConfig;
import net.momirealms.sparrow.proxy.common.redis.RedisConnector;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "sparrow",
        name = "Sparrow",
        version = BuildInfo.VERSION,
        authors = {"XiaoMoMi"}
)
public final class VelocitySparrow {
    private final Logger logger;
    private final Path dataDirectory;
    private RedisConnector redis;

    @Inject
    public VelocitySparrow(Logger logger, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        ProxyConfig config = ProxyConfig.load(this.dataDirectory);
        this.redis = new RedisConnector(config.redis());
        this.redis.initialize();
        this.logger.info("Connected to Redis");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        // 读取配置失败时连接器还没有创建
        if (this.redis != null) this.redis.close();
    }
}

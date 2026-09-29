package net.momirealms.sparrow.proxy.bungeecord;

import net.md_5.bungee.api.plugin.Plugin;
import net.momirealms.sparrow.proxy.common.config.ProxyConfig;
import net.momirealms.sparrow.proxy.common.redis.RedisConnector;

public final class BungeeCordSparrow extends Plugin {
    private RedisConnector redis;

    @Override
    public void onEnable() {
        ProxyConfig config = ProxyConfig.load(this.getDataFolder().toPath());
        this.redis = new RedisConnector(config.redis());
        this.redis.initialize();
        this.getLogger().info("Connected to Redis");
    }

    @Override
    public void onDisable() {
        // 读取配置失败时连接器还没有创建
        if (this.redis != null) this.redis.close();
    }
}

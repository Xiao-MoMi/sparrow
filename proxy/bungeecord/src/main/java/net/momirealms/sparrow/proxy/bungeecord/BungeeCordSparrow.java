package net.momirealms.sparrow.proxy.bungeecord;

import net.md_5.bungee.api.plugin.Plugin;
import net.momirealms.sparrow.proxy.common.ProxyPlatform;
import net.momirealms.sparrow.proxy.common.SparrowProxy;
import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class BungeeCordSparrow extends Plugin implements ProxyPlatform {
    private final SparrowProxy sparrow = new SparrowProxy(this, new BungeeCordPlayerManager(this));

    @Override
    public void onEnable() {
        this.sparrow.enable();
    }

    @Override
    public void onDisable() {
        this.sparrow.disable();
    }

    @Override
    @NotNull
    public Path dataFolderPath() {
        return this.getDataFolder().toPath();
    }

    // 插件实例创建后才会注入平台日志, 所以按需包装
    @Override
    @NotNull
    public ProxyLogger logger() {
        return new JavaProxyLogger(this.getLogger());
    }

}

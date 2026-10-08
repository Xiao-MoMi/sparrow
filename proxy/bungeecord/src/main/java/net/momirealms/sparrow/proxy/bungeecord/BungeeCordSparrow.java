package net.momirealms.sparrow.proxy.bungeecord;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.momirealms.sparrow.proxy.common.ProxyPlatform;
import net.momirealms.sparrow.proxy.common.SparrowProxy;
import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;

public final class BungeeCordSparrow extends Plugin implements ProxyPlatform {
    private final SparrowProxy sparrow = new SparrowProxy(this);

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

    @Override
    public void disconnect(@NotNull UUID player, @NotNull String jsonReason) {
        ProxiedPlayer target = this.getProxy().getPlayer(player);
        if (target != null) {
            target.disconnect(ComponentSerializer.deserialize(jsonReason));
        }
    }
}

package net.momirealms.sparrow.proxy.common.player;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class ProxyPlayerManager {

    public final void enable() {
        this.registerListeners();
    }

    // 需要代理事件判定切服结果的平台覆盖
    protected void registerListeners() {
    }

    public void disable() {
    }

    @NotNull
    public abstract CompletableFuture<ConnectResult> connect(@NotNull UUID player, @NotNull String sourceServer, @NotNull String targetServer);

    public abstract boolean disconnect(@NotNull UUID player, @NotNull String jsonReason);
}
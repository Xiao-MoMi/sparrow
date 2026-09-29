package net.momirealms.sparrow.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.momirealms.sparrow.proxy.common.BuildInfo;
import net.momirealms.sparrow.proxy.common.ProxyPlatform;
import net.momirealms.sparrow.proxy.common.SparrowProxy;
import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;

@Plugin(
        id = "sparrow",
        name = "Sparrow",
        version = BuildInfo.VERSION,
        authors = {"XiaoMoMi"}
)
public final class VelocitySparrow implements ProxyPlatform {
    private final ProxyServer server;
    private final ProxyLogger logger;
    private final Path dataDirectory;
    private final SparrowProxy sparrow;

    @Inject
    public VelocitySparrow(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = new Slf4jProxyLogger(logger);
        this.dataDirectory = dataDirectory;
        this.sparrow = new SparrowProxy(this);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.sparrow.enable();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        this.sparrow.disable();
    }

    @Override
    @NotNull
    public Path dataFolderPath() {
        return this.dataDirectory;
    }

    @Override
    @NotNull
    public ProxyLogger logger() {
        return this.logger;
    }

    @Override
    public void disconnect(@NotNull UUID player, @NotNull String jsonReason) {
        this.server.getPlayer(player).ifPresent(target -> target.disconnect(GsonComponentSerializer.gson().deserialize(jsonReason)));
    }
}

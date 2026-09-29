package net.momirealms.sparrow.proxy.common;

import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;

public interface ProxyPlatform {

    @NotNull
    Path dataFolderPath();

    @NotNull
    ProxyLogger logger();

    void disconnect(@NotNull UUID player, @NotNull String jsonReason);
}

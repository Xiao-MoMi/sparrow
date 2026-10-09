package net.momirealms.sparrow.proxy.common;

import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface ProxyPlatform {

    @NotNull
    Path dataFolderPath();

    @NotNull
    ProxyLogger logger();

}

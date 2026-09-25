package net.momirealms.sparrow.feature.server;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ServerFeature extends Feature<ServerSettings> {
    public static final String ID = "server";

    private final FeaturesConfig featuresConfig;

    public ServerFeature(@NotNull FeaturesConfig featuresConfig) {
        super(ID);
        this.featuresConfig = featuresConfig;
    }

    @Override
    public void loadConfig() {
        this.config = this.featuresConfig.config().server();
    }

    /**
     * 判断服务器是否在配置允许的切换范围内.
     *
     * @param server 目标服务器名
     * @return 允许列表为空或包含该服务器时为 {@code true}
     */
    public boolean allowed(@NotNull String server) {
        List<String> allowed = this.config().allowedServers();
        return allowed.isEmpty() || allowed.contains(server);
    }
}

package net.momirealms.sparrow.feature.server;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public final class ServerFeature extends Feature<ServerSettings> {
    public static final String FEATURE_ID = "server";

    private final SparrowPlugin plugin;

    public ServerFeature(@NotNull SparrowPlugin plugin) {
        super(FEATURE_ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        this.config = this.plugin.configurationManager().featuresConfig().config().server();
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new ServerCommand(this.plugin.commandManager(), this.plugin));
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

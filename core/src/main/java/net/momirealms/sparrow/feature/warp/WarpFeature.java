package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class WarpFeature implements Feature {
    public static final String ID = "warp";
    private static final String USE_PERMISSION = DependencyVersions.PROJECT_ID + ".warp.use.";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private volatile WarpConfig config;
    private volatile WarpRegistry registry;
    private WarpService service;

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supportsHotEnable() {
        return true;
    }

    @Override
    public boolean supportsHotDisable() {
        return true;
    }

    private WarpConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/warp.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(WarpSettings.class, WarpSettings::new)
                    .load(path)
                    .value()
                    .toConfig();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onEnable() {
        WarpConfig loaded = this.readConfig();
        WarpStore store = this.plugin.dataStorage().warpStore();
        WarpRegistry registry = new WarpRegistry(store);
        this.config = loaded;
        this.registry = registry;
        this.service = new WarpService(this, store, registry);
        return registry.load().whenComplete((ignored, error) -> {
            if (error != null) registry.close();
        });
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onDisable() {
        WarpRegistry registry = this.registry;
        if (registry != null) registry.close();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    public WarpRegistry registry() {
        return this.registry;
    }

    @NotNull
    public WarpService service() {
        return this.service;
    }

    WarpConfig config() {
        return this.config;
    }

    public boolean visible(@NotNull CommandSender sender, @NotNull Warp warp) {
        return !this.config.permissionRestrict() || sender.hasPermission(USE_PERMISSION + warp.getNameKey());
    }

    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String input) {
        return this.registry.complete(input, warp -> this.visible(sender, warp));
    }

    void accept(WarpMessage message) {
        WarpRegistry registry = this.registry;
        if (registry == null || message.origin().equals(ServerConfig.serverId())) return;
        registry.refresh(message.warpId()).whenComplete((ignored, error) -> {
            if (error != null) this.plugin.logger().warn("Failed to refresh warp " + message.warpId(), error);
        });
    }
}
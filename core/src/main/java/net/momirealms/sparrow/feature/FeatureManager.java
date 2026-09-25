package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.feature.patrol.PatrolFeature;
import net.momirealms.sparrow.feature.head.HeadFeature;
import net.momirealms.sparrow.feature.highlight.HighlightFeature;
import net.momirealms.sparrow.feature.quickshulker.QuickShulkerFeature;
import net.momirealms.sparrow.feature.server.ServerFeature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import net.momirealms.sparrow.util.ExceptionCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class FeatureManager {
    private final FeaturesConfig config;
    private final Executor asyncExecutor;
    private final Executor platformExecutor;
    private final Map<String, Feature<?>> features = new LinkedHashMap<>();
    private volatile boolean closed;

    public FeatureManager(@NotNull SparrowPlugin plugin) {
        this.config = plugin.configurationManager().featuresConfig();
        this.asyncExecutor = plugin.scheduler().async();
        this.platformExecutor = plugin.scheduler().platform();

        this.features.put(QuickShulkerFeature.ID, new QuickShulkerFeature(plugin.javaPlugin(), this.config));
        this.features.put(PatrolFeature.ID, new PatrolFeature(plugin.javaPlugin(), this.config));
        this.features.put(ServerFeature.ID, new ServerFeature(this.config));
        this.features.put(HighlightFeature.ID, new HighlightFeature(plugin));
        this.features.put(HeadFeature.ID, new HeadFeature(plugin));
    }

    public void onEnable() {
        for (Feature<?> feature : this.features.values()) {
            feature.install();
        }
    }

    public void onReloadStart() {
        this.requireOpen();
        for (Feature<?> feature : this.features.values()) {
            if (feature.hotToggleable()) {
                feature.stop();
            }
        }
    }

    public void onReloadAsync() {
        this.requireOpen();
        for (Feature<?> feature : this.features.values()) {
            if (feature.hotToggleable() && feature.installed()) {
                feature.loadConfig();
            }
        }
    }

    public void onReloadFinish() {
        this.requireOpen();
        for (Feature<?> feature : this.features.values()) {
            if (feature.hotToggleable()) {
                if (!feature.installed()) {
                    feature.install();
                } else if (feature.config().enabled()) {
                    feature.start();
                }
            }
        }
    }

    public void onDisable() {
        this.closed = true;
        ExceptionCollector<RuntimeException> failures = new ExceptionCollector<>(RuntimeException.class);
        for (Feature<?> feature : this.features.values()) {
            try {
                feature.uninstall();
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        failures.throwIfPresent();
    }

    @NotNull
    public CompletableFuture<FeatureState> setEnabled(@NotNull String id, boolean enabled) {
        this.requireOpen();
        Feature<?> feature = this.features.get(id);
        if (feature == null) {
            throw new IllegalArgumentException("Unknown feature: " + id);
        }
        if (!feature.hotToggleable()) {
            throw new IllegalArgumentException("Feature requires a restart: " + id);
        }
        this.config.saveEnabled(id, enabled);
        if (!enabled) {
            feature.stop();
        } else if (!feature.installed()) {
            feature.install();
        } else if (!feature.enabled()) {
            return CompletableFuture.runAsync(() -> {
                this.requireOpen();
                feature.loadConfig();
            }, this.asyncExecutor).thenApplyAsync(ignored -> {
                this.requireOpen();
                feature.start();
                return feature.state().get();
            }, this.platformExecutor);
        }
        return CompletableFuture.completedFuture(feature.state().get());
    }

    private void requireOpen() {
        if (this.closed) {
            throw new IllegalStateException("Feature manager is shut down");
        }
    }

    @Nullable
    public Feature<?> feature(@NotNull String id) {
        return this.features.get(id);
    }

    /**
     * 按 ID 获取已注册的内置模块并转换为具体类型, 供模块自带的命令访问业务方法.
     *
     * @param id <strong>必须是已注册的模块 ID</strong>
     * @param type 模块的具体类型
     * @return 对应的模块实例
     */
    @NotNull
    public <F extends Feature<?>> F feature(@NotNull String id, @NotNull Class<F> type) {
        return type.cast(this.features.get(id));
    }

    @NotNull
    public Collection<String> ids() {
        return Collections.unmodifiableSet(this.features.keySet());
    }
}

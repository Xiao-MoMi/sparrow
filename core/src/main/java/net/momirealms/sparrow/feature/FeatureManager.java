package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.feature.highlight.HighlightFeature;
import net.momirealms.sparrow.feature.quickshulker.QuickShulkerFeature;
import net.momirealms.sparrow.feature.playerlimit.PlayerLimitFeature;
import net.momirealms.sparrow.feature.maintenance.MaintenanceFeature;
import net.momirealms.sparrow.feature.head.HeadFeature;
import net.momirealms.sparrow.feature.patrol.PatrolFeature;
import net.momirealms.sparrow.feature.rtp.RtpFeature;
import net.momirealms.sparrow.feature.back.BackFeature;
import net.momirealms.sparrow.feature.spawn.SpawnFeature;
import net.momirealms.sparrow.feature.tpa.TpaFeature;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ModulesConfig;
import net.momirealms.sparrow.util.ExceptionCollector;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class FeatureManager {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Map<String, Entry> features = new LinkedHashMap<>();
    private CompletableFuture<?> operation = CompletableFuture.completedFuture(null);
    private boolean reloading;
    private volatile boolean closed;

    public FeatureManager() {
        this.features.put(HighlightFeature.ID, new Entry(new HighlightFeature()));
        this.features.put(QuickShulkerFeature.ID, new Entry(new QuickShulkerFeature()));
        this.features.put(PlayerLimitFeature.ID, new Entry(new PlayerLimitFeature()));
        this.features.put(MaintenanceFeature.ID, new Entry(new MaintenanceFeature()));
        this.features.put(HeadFeature.ID, new Entry(new HeadFeature()));
        this.features.put(PatrolFeature.ID, new Entry(new PatrolFeature()));
        this.features.put(RtpFeature.ID, new Entry(new RtpFeature()));
        this.features.put(BackFeature.ID, new Entry(new BackFeature()));
        this.features.put(SpawnFeature.ID, new Entry(new SpawnFeature()));
        this.features.put(TpaFeature.ID, new Entry(new TpaFeature()));
        this.features.put(WarpFeature.ID, new Entry(new WarpFeature()));
    }

    public void onLoad() {
        Set<String> enabled = ModulesConfig.loadEnabled();
        for (Entry entry : this.features.values()) {
            entry.startupEnabled = enabled.contains(entry.feature.id());
            if (!entry.startupEnabled) continue;
            entry.participated = true;
            try {
                entry.feature.onPluginLoad();
                entry.state = FeatureState.DISABLED;
            } catch (RuntimeException exception) {
                entry.state = FeatureState.FAILED;
                throw exception;
            }
        }
    }

    @NotNull
    public CompletableFuture<Void> onEnable() {
        return this.execute(false, () -> {
            List<CompletableFuture<Void>> starting = new ArrayList<>();
            for (Entry entry : this.features.values()) {
                if (entry.startupEnabled) {
                    starting.add(this.change(entry, true));
                }
            }
            return CompletableFuture.allOf(starting.toArray(CompletableFuture[]::new));
        });
    }

    @NotNull
    public CompletableFuture<ReloadResult> onReload(@NotNull Runnable reloadConfiguration, @NotNull Executor asyncExecutor, @NotNull Executor lifecycleExecutor) {
        return this.execute(true, () -> {
            long started = System.currentTimeMillis();
            return CompletableFuture.supplyAsync(
                    () -> {
                        Set<String> enabled = ModulesConfig.loadEnabled();
                        reloadConfiguration.run();
                        return enabled;
                    },
                    asyncExecutor)
                    .thenComposeAsync(
                            enabled -> {
                                long applying = System.currentTimeMillis();
                                return this.applyReload(enabled)
                                        .thenApply(restartRequired -> new ReloadResult(applying - started, System.currentTimeMillis() - applying, restartRequired));
                            },
                            lifecycleExecutor
                    );
        });
    }

    private synchronized CompletableFuture<List<String>> applyReload(Set<String> enabled) {
        if (this.closed) throw new IllegalStateException("Feature manager is shut down");
        List<CompletableFuture<Void>> reloading = new ArrayList<>();
        List<String> restartRequired = new ArrayList<>();
        for (Entry entry : this.features.values()) {
            String id = entry.feature.id();
            boolean target = enabled.contains(id);
            boolean current = entry.state == FeatureState.ENABLED;
            if (entry.state == FeatureState.FAILED || (current != target && (target ? !entry.feature.supportsHotEnable() : !entry.feature.supportsHotDisable()))) {
                restartRequired.add(id);
                this.plugin.logger().warn("Feature '" + id + "' requires a restart to apply modules.yml (state=" + entry.state + ", enabled=" + target + ")");
                continue;
            }
            if (current != target) {
                this.change(entry, target);
            } else if (current) {
                entry.operation = CompletableFuture.completedFuture(null).thenCompose(ignored -> entry.feature.onReload());
            } else {
                continue;
            }
            reloading.add(entry.operation.exceptionally(error -> {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                throw new IllegalStateException("Failed to reload feature '" + id + "'", cause);
            }));
        }
        return CompletableFuture.allOf(reloading.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> this.refreshCommands())
                .thenApply(ignored -> List.copyOf(restartRequired));
    }

    @NotNull
    public CompletableFuture<FeatureState> setEnabled(@NotNull String id, boolean enabled) {
        return this.execute(false, () -> {
            Entry entry = this.getEntry(id);
            if (enabled ? !entry.feature.supportsHotEnable() : !entry.feature.supportsHotDisable()) {
                throw new IllegalArgumentException("Feature requires a restart: " + id);
            }
            if (entry.state == FeatureState.FAILED) throw new IllegalStateException("Feature has failed: " + id);
            if (this.isEnabled(id) == enabled) return CompletableFuture.completedFuture(entry.state);
            return this.change(entry, enabled)
                    .whenComplete((ignored, error) -> this.refreshCommands())
                    .thenApply(ignored -> enabled ? FeatureState.ENABLED : FeatureState.DISABLED);
        });
    }

    private synchronized <T> CompletableFuture<T> execute(boolean reloading, Supplier<CompletableFuture<T>> action) {
        this.requireIdle();
        // 占用管理入口后再调用模块, 同步回调和异步阶段都属于本次操作
        CompletableFuture<T> result = new CompletableFuture<>();
        this.operation = result;
        this.reloading = reloading;
        CompletableFuture.completedFuture(null)
                .thenCompose(ignored -> action.get())
                .whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
        return result.copy();
    }

    private CompletableFuture<Void> change(Entry entry, boolean enabled) {
        entry.participated = true;
        if (enabled) entry.enableAttempted = true;
        if (!enabled) entry.state = FeatureState.DISABLED;
        entry.operation = CompletableFuture.completedFuture(null)
                .thenCompose(ignored -> enabled ? entry.feature.onEnable() : entry.feature.onDisable())
                .whenComplete((ignored, error) -> {
                    entry.state = error == null ? (enabled ? FeatureState.ENABLED : FeatureState.DISABLED) : FeatureState.FAILED;
                    if (!enabled && error == null) entry.enableAttempted = false;
                });
        return entry.operation;
    }

    public void onDisable() {
        synchronized (this) {
            this.closed = true;
        }
        ExceptionCollector<RuntimeException> failures = new ExceptionCollector<>(RuntimeException.class);
        for (Entry entry : this.features.values()) {
            if (!entry.participated) continue;
            // 等已进入模块的回调结束, 保留会话收尾需要的数据服务
            entry.operation.handle((ignored, error) -> null).join();
            try {
                if (entry.enableAttempted) {
                    this.change(entry, false).join();
                }
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        failures.throwIfPresent();
    }

    // 玩家会话全部释放后, 再结束参与过初始化的模块数据服务
    public void onPluginShutdown() {
        ExceptionCollector<RuntimeException> failures = new ExceptionCollector<>(RuntimeException.class);
        for (Entry entry : this.features.values()) {
            if (!entry.participated) continue;
            entry.participated = false;
            try {
                entry.feature.onPluginShutdown().join();
            } catch (RuntimeException exception) {
                failures.add(exception);
            } finally {
                entry.state = FeatureState.UNLOADED;
            }
        }
        failures.throwIfPresent();
    }

    public synchronized void refreshCommands() {
        if (this.closed || !this.plugin.javaPlugin().isEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.plugin.scheduler().platform().run(player::updateCommands, () -> {}, player);
        }
    }

    public synchronized boolean isBusy() {
        return !this.operation.isDone();
    }

    public synchronized boolean isReloading() {
        return this.reloading && this.isBusy();
    }

    private void requireIdle() {
        if (this.closed) throw new IllegalStateException("Feature manager is shut down");
        if (this.isBusy()) throw new IllegalStateException("Feature operation is in progress");
    }

    private Entry getEntry(String id) {
        Entry entry = this.features.get(id);
        if (entry == null) throw new IllegalArgumentException("Unknown feature: " + id);
        return entry;
    }

    public boolean isEnabled(@NotNull String id) {
        return !this.closed && this.getState(id) == FeatureState.ENABLED;
    }

    @NotNull
    public FeatureState getState(@NotNull String id) {
        return this.getEntry(id).state;
    }

    @Nullable
    public Feature feature(@NotNull String id) {
        Entry entry = this.features.get(id);
        return entry == null ? null : entry.feature;
    }

    @NotNull
    public <F extends Feature> F feature(@NotNull String id, @NotNull Class<F> type) {
        return type.cast(this.getEntry(id).feature);
    }

    @NotNull
    public Collection<String> ids() {
        return Collections.unmodifiableSet(this.features.keySet());
    }

    public record ReloadResult(long asyncTime, long syncTime, @NotNull List<String> restartRequired) {
    }

    private static final class Entry {
        private final Feature feature;
        private volatile FeatureState state = FeatureState.UNINSTALLED;
        private volatile CompletableFuture<Void> operation = CompletableFuture.completedFuture(null);
        private boolean startupEnabled;
        private boolean participated;
        private volatile boolean enableAttempted;

        private Entry(Feature feature) {
            this.feature = feature;
        }
    }
}
package net.momirealms.sparrow.feature;

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

public final class FeatureManager {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Map<String, Entry> features = new LinkedHashMap<>();
    private boolean closed;

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
        List<CompletableFuture<Void>> starting = new ArrayList<>();
        for (Entry entry : this.features.values()) {
            if (entry.startupEnabled) starting.add(this.change(entry, true));
        }
        return CompletableFuture.allOf(starting.toArray(CompletableFuture[]::new));
    }

    @NotNull
    public synchronized CompletableFuture<Void> onReload() {
        this.requireIdle();
        List<CompletableFuture<Void>> reloading = new ArrayList<>();
        for (Entry entry : this.features.values()) {
            if (entry.state != FeatureState.ENABLED) continue;
            entry.operation = CompletableFuture.completedFuture(null).thenCompose(ignored -> entry.feature.onReload());
            reloading.add(entry.operation);
        }
        return CompletableFuture.allOf(reloading.toArray(CompletableFuture[]::new)).copy();
    }

    @NotNull
    public synchronized CompletableFuture<FeatureState> setEnabled(@NotNull String id, boolean enabled) {
        this.requireIdle();
        Entry entry = this.getEntry(id);
        if (enabled ? !entry.feature.supportsHotEnable() : !entry.feature.supportsHotDisable()) {
            throw new IllegalArgumentException("Feature requires a restart: " + id);
        }
        if (entry.state == FeatureState.FAILED) throw new IllegalStateException("Feature has failed: " + id);
        if (this.isEnabled(id) == enabled) return CompletableFuture.completedFuture(entry.state);
        return this.change(entry, enabled).thenApply(ignored -> {
            this.refreshCommands();
            return enabled ? FeatureState.ENABLED : FeatureState.DISABLED;
        });
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
        return entry.operation.copy();
    }

    public void onDisable() {
        synchronized (this) {
            this.closed = true;
        }
        ExceptionCollector<RuntimeException> failures = new ExceptionCollector<>(RuntimeException.class);
        for (Entry entry : this.features.values()) {
            if (!entry.participated) continue;
            // 先结束已接受的回调, 再按当前状态清理这个模块
            entry.operation.handle((ignored, error) -> null).join();
            try {
                if (entry.enableAttempted) {
                    this.change(entry, false).join();
                }
            } catch (RuntimeException exception) {
                failures.add(exception);
            } finally {
                entry.participated = false;
                entry.state = FeatureState.UNLOADED;
                try {
                    entry.feature.onPluginShutdown().join();
                } catch (RuntimeException exception) {
                    failures.add(exception);
                }
            }
        }
        failures.throwIfPresent();
    }

    public void refreshCommands() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Spigot 的命令树更新会触发同步 PlayerCommandSendEvent.
            this.plugin.scheduler().platform().run(player::updateCommands, () -> {}, player);
        }
    }

    public synchronized boolean isBusy() {
        for (Entry entry : this.features.values()) {
            if (!entry.operation.isDone()) return true;
        }
        return false;
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
        return this.getState(id) == FeatureState.ENABLED;
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
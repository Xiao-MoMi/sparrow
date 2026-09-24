package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.ui.state.MutableSignal;
import net.momirealms.sparrow.ui.state.Signal;
import org.jetbrains.annotations.NotNull;

public abstract class Feature<C extends FeatureSettings> {
    private final String id;
    private final MutableSignal<FeatureState> state = Signal.of(FeatureState.UNINSTALLED);
    protected volatile C config;
    private boolean installed;

    protected Feature(@NotNull String id) {
        this.id = id;
    }

    // 同步读取配置, 按开关安装并启动功能.
    final void install() {
        if (this.installed) return;
        this.loadConfig();
        if (!this.config.enabled()) return;
        try {
            this.installed = true;
            this.onLoad();
            this.state.set(FeatureState.DISABLED);
            this.start();
        } catch (RuntimeException exception) {
            this.state.set(FeatureState.FAILED);
            throw exception;
        }
    }

    // 配置加载完成后同步启动已安装的功能.
    final void start() {
        FeatureState current = this.state.get();
        if (current == FeatureState.ENABLED) return;
        if (!this.installed || current != FeatureState.DISABLED) {
            throw new IllegalStateException("Feature cannot be enabled in state " + current + ": " + this.id);
        }
        try {
            this.onEnable();
            this.state.set(FeatureState.ENABLED);
        } catch (RuntimeException exception) {
            this.state.set(FeatureState.FAILED);
            throw exception;
        }
    }

    final void stop() {
        FeatureState current = this.state.get();
        if (!this.installed || current == FeatureState.DISABLED) return;
        this.state.set(FeatureState.DISABLED);
        try {
            this.onDisable();
        } catch (RuntimeException exception) {
            this.state.set(FeatureState.FAILED);
            throw exception;
        }
    }

    final void uninstall() {
        if (!this.installed) return;
        try {
            this.stop();
        } finally {
            this.installed = false;
            this.state.set(FeatureState.UNLOADED);
            this.onUnload();
        }
    }

    public abstract void loadConfig();

    protected void onLoad() {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onUnload() {
    }

    @NotNull
    public final String id() {
        return this.id;
    }

    @NotNull
    public final C config() {
        return this.config;
    }

    @NotNull
    public final Signal<FeatureState> state() {
        return this.state;
    }

    public final boolean enabled() {
        return this.state.get() == FeatureState.ENABLED;
    }

    public final boolean installed() {
        return this.installed;
    }

    public boolean hotToggleable() {
        return true;
    }
}

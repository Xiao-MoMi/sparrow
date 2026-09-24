package net.momirealms.sparrow.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public final class PaperJavaPlugin extends JavaPlugin {
    private final PaperBootstrap bootstrap;

    public PaperJavaPlugin(PaperBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        this.bootstrap.plugin.setJavaPlugin(this);
    }

    @Override
    public void onLoad() {
        this.bootstrap.plugin.onPluginLoad();
    }

    @Override
    public void onEnable() {
        this.bootstrap.plugin.onPluginEnable();
    }

    @Override
    public void onDisable() {
        this.bootstrap.plugin.onPluginDisable();
    }
}

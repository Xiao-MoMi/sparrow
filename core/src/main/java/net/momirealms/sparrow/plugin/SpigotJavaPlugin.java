package net.momirealms.sparrow.plugin;

import net.momirealms.sparrow.plugin.classpath.ReflectionClassPathAppender;
import net.momirealms.sparrow.plugin.classpath.URLClassPathAppender;
import net.momirealms.sparrow.plugin.logger.JavaPluginLogger;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SpigotJavaPlugin extends JavaPlugin {
    SparrowPlugin plugin;

    @Override
    public void onLoad() {
        PluginLogger logger = new JavaPluginLogger(this.getLogger());
        this.plugin = new SparrowPlugin(
                logger,
                this.getDataFolder().toPath().toAbsolutePath(),
                new URLClassPathAppender(Bukkit.class.getClassLoader()),
                new ReflectionClassPathAppender(this.getClass().getClassLoader())
        );
        this.plugin.setJavaPlugin(this);
        this.plugin.onPluginLoad();
    }

    @Override
    public void onEnable() {
        this.plugin.onPluginEnable();
    }

    @Override
    public void onDisable() {
        this.plugin.onPluginDisable();
    }
}

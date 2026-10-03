package net.momirealms.sparrow.testutil;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;

import java.lang.reflect.Field;

public final class PluginTestContext implements AutoCloseable {
    private final Field instance;
    private final Field config;
    private final Object previousPlugin;
    private final Object previousConfig;

    public PluginTestContext(SparrowPlugin plugin, String serverId) throws ReflectiveOperationException {
        this.instance = SparrowPlugin.class.getDeclaredField("instance");
        this.instance.setAccessible(true);
        this.config = ServerConfig.class.getDeclaredField("config");
        this.config.setAccessible(true);
        this.previousPlugin = this.instance.get(null);
        this.previousConfig = this.config.get(null);
        ServerConfig.ConfigDefinition configuration = new ServerConfig.ConfigDefinition();
        setField(configuration, "serverId", serverId);
        this.instance.set(null, plugin);
        this.config.set(null, configuration);
    }

    public static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Override
    public void close() throws IllegalAccessException {
        this.instance.set(null, this.previousPlugin);
        this.config.set(null, this.previousConfig);
    }
}

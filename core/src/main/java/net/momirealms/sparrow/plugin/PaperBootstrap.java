package net.momirealms.sparrow.plugin;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import net.momirealms.sparrow.plugin.classpath.BukkitClassPathAppender;
import net.momirealms.sparrow.plugin.classpath.PaperPluginClassPathAppender;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.plugin.logger.Slf4jPluginLogger;
import net.momirealms.sparrow.util.ReflectionUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public final class PaperBootstrap implements PluginBootstrap {
    private static final Class<?> clazz$PluginProviderContext = PluginProviderContext.class;
    private static final Class<?> clazz$ComponentLogger = Objects.requireNonNull(
            ReflectionUtils.getClazz("net{}kyori{}adventure{}text{}logger{}slf4j{}ComponentLogger".replace("{}", "."))
    );
    private static final Method method$PluginProviderContext$getLogger = Objects.requireNonNull(
            ReflectionUtils.getMethod(clazz$PluginProviderContext, clazz$ComponentLogger, new String[] { "getLogger" })
    );

    SparrowPlugin plugin;

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        PluginLogger logger;
        // 创建 Logger
        try {
            logger = new Slf4jPluginLogger((org.slf4j.Logger) method$PluginProviderContext$getLogger.invoke(context));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to getLogger", e);
        }
        // 创建 Plugin
        this.plugin = new SparrowPlugin(
                logger,
                context.getDataDirectory(),
                new BukkitClassPathAppender(),
                new PaperPluginClassPathAppender(this.getClass().getClassLoader())
        );
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new PaperJavaPlugin(this);
    }
}

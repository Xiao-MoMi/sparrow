package net.momirealms.sparrow.plugin.classpath;


import net.momirealms.sparrow.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Optional;

public final class PaperPluginClassPathAppender implements ClassPathAppender {
    public static final Class<?> clazz$PaperPluginClassLoader = ReflectionUtils.getClazz(
            "io.papermc.paper.plugin.entrypoint.classloader.PaperPluginClassLoader"
    );
    public static final Field field$PaperPluginClassLoader$libraryLoader = Optional.ofNullable(clazz$PaperPluginClassLoader)
            .map(it -> ReflectionUtils.getDeclaredField(it, URLClassLoader.class, 0))
            .orElse(null);
    private final URLClassLoaderAccess libraryClassLoaderAccess;

    public PaperPluginClassPathAppender(ClassLoader classLoader) {
        try {
            // 使用paper自带的classloader去加载依赖，这种情况会发生依赖隔离
            if (clazz$PaperPluginClassLoader != null && clazz$PaperPluginClassLoader.isInstance(classLoader)) {
                URLClassLoader libraryClassLoader = (URLClassLoader) field$PaperPluginClassLoader$libraryLoader.get(classLoader);
                this.libraryClassLoaderAccess = URLClassLoaderAccess.create(libraryClassLoader);
            } else {
                throw new IllegalStateException("ClassLoader is not instance of URLClassLoader");
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate PaperPluginClassLoader", e);
        }
    }

    @Override
    public void addJarToClasspath(Path file) {
        try {
            this.libraryClassLoaderAccess.addURL(file.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}

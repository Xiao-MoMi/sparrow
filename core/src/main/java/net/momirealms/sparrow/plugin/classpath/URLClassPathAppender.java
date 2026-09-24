package net.momirealms.sparrow.plugin.classpath;

import net.momirealms.sparrow.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;

public final class URLClassPathAppender implements ClassPathAppender {
    private final Collection<URL> unopenedURLs;
    private final Collection<URL> pathURLs;

    @SuppressWarnings({"unchecked", "resource"})
    public URLClassPathAppender(ClassLoader classLoader) {
        try {
            URLClassLoader urlClassLoader = findURLClassLoader(classLoader); // 兼容类似ignite的加载器
            if (urlClassLoader != null) classLoader = urlClassLoader;
            Field field = findURLClassPathField(classLoader.getClass()); // 尝试获取ucp字段
            Object ucp = ReflectionUtils.LOOKUP.unreflectGetter(field).invoke(classLoader);
            this.unopenedURLs = (Collection<URL>) ReflectionUtils.LOOKUP.unreflectGetter(ucp.getClass().getDeclaredField("unopenedUrls")).invoke(ucp);
            this.pathURLs = (Collection<URL>) ReflectionUtils.LOOKUP.unreflectGetter(ucp.getClass().getDeclaredField("path")).invoke(ucp);
        } catch (Throwable t) {
            throw new UnsupportedOperationException("Unsupported classloader " + classLoader.getClass(), t);
        }
    }

    private static Field findURLClassPathField(Class<?> clazz) {
        if (clazz == null) return null;
        try {
            return clazz.getDeclaredField("ucp");
        } catch (NoSuchFieldException ignored) {
        }
        return findURLClassPathField(clazz.getSuperclass());
    }

    private static URLClassLoader findURLClassLoader(ClassLoader classLoader) {
        if (classLoader instanceof URLClassLoader urlClassLoader) return urlClassLoader;
        ClassLoader parent = classLoader.getParent();
        return parent == null ? null : findURLClassLoader(parent);
    }

    @Override
    public void addJarToClasspath(Path file) {
        try {
            URL url = file.toUri().toURL();
            synchronized (this.unopenedURLs)  {
                this.unopenedURLs.add(url);
                this.pathURLs.add(url);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}

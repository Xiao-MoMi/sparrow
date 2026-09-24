package net.momirealms.sparrow.plugin.classpath;

import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * 该类通过内部 URLClassLoaderAccess 动态地将 jar 文件 URL 注入到现有的 URLClassLoader 中.
 */
public class ReflectionClassPathAppender implements ClassPathAppender {
    private final URLClassLoaderAccess classLoaderAccess;

    /**
     * 构造函数, 初始化基于反射的类路径追加器.
     *
     * @param classLoader 目标类加载器, 必须是 URLClassLoader 及其子类.
     * @throws IllegalStateException 如果传入的 classLoader 不是 URLClassLoader 的实例.
     */
    public ReflectionClassPathAppender(ClassLoader classLoader) throws IllegalStateException {
        if (classLoader instanceof URLClassLoader) {
            this.classLoaderAccess = URLClassLoaderAccess.create((URLClassLoader) classLoader);
        } else {
            throw new IllegalStateException("ClassLoader is not instance of URLClassLoader");
        }
    }

    /**
     * 将指定的 jar 文件添加到类路径中.
     *
     * @param file 需要添加到类路径的 jar 文件路径.
     * @throws RuntimeException 如果在将 Path 转换为 URL 时发生 MalformedURLException.
     */
    @Override
    public void addJarToClasspath(Path file) {
        try {
            this.classLoaderAccess.addURL(file.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}

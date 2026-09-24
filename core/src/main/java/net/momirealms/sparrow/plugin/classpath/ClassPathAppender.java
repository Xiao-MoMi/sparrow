package net.momirealms.sparrow.plugin.classpath;

import java.nio.file.Path;

/**
 * 类路径追加器接口.
 * 用于在运行时向类加载器的类路径中动态添加 jar 文件.
 */
public interface ClassPathAppender extends AutoCloseable {

    /**
     * 将指定的 jar 文件添加到类路径中.
     *
     * @param file 需要添加到类路径的 jar 文件路径.
     */
    void addJarToClasspath(Path file);

    /**
     * 关闭类路径追加器并释放相关资源.
     */
    @Override
    default void close() {
    }
}

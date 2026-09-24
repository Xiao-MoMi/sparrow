package net.momirealms.sparrow.plugin.dependency.relocation;

import net.momirealms.sparrow.plugin.dependency.Dependencies;
import net.momirealms.sparrow.plugin.dependency.Dependency;
import net.momirealms.sparrow.plugin.dependency.DependencyManager;
import net.momirealms.sparrow.plugin.dependency.classloader.IsolatedClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RelocationHandler {
    public static final Set<Dependency> DEPENDENCIES = Set.of(Dependencies.ASM, Dependencies.ASM_COMMONS, Dependencies.JAR_RELOCATOR);
    private static final String JAR_RELOCATOR_CLASS = "me.lucko.jarrelocator.JarRelocator";
    private static final String JAR_RELOCATOR_RUN_METHOD = "run";

    private final Constructor<?> jarRelocatorConstructor;
    private final Method jarRelocatorRunMethod;

    /**
     * 初始化重定位处理器, 加载相关的依赖项并获取 jar-relocator 的构造函数和执行方法.
     *
     * @param dependencyManager 依赖管理器
     */
    public RelocationHandler(DependencyManager dependencyManager) {
        ClassLoader classLoader = null;
        try {
            dependencyManager.loadDependencies(DEPENDENCIES);
            classLoader = dependencyManager.obtainClassLoaderWith(DEPENDENCIES);
            Class<?> jarRelocatorClass = classLoader.loadClass(JAR_RELOCATOR_CLASS);
            this.jarRelocatorConstructor = jarRelocatorClass.getDeclaredConstructor(File.class, File.class, Map.class);
            this.jarRelocatorConstructor.setAccessible(true);
            this.jarRelocatorRunMethod = jarRelocatorClass.getDeclaredMethod(JAR_RELOCATOR_RUN_METHOD);
            this.jarRelocatorRunMethod.setAccessible(true);
        } catch (Exception e) {
            try {
                if (classLoader instanceof IsolatedClassLoader isolatedClassLoader) {
                    isolatedClassLoader.close();
                }
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 将输入的 jar 文件根据提供的重定位规则重新映射并输出到指定路径.
     *
     * @param input       输入的 jar 文件路径
     * @param output      输出的 jar 文件路径
     * @param relocations 重定位规则列表
     * @throws Exception 如果在重映射过程中发生错误
     */
    public void remap(Path input, Path output, List<Relocation> relocations) throws Exception {
        Map<String, String> mappings = new HashMap<>();
        for (Relocation relocation : relocations) {
            mappings.put(relocation.getPattern(), relocation.getRelocatedPattern());
        }

        Object relocator = this.jarRelocatorConstructor.newInstance(input.toFile(), output.toFile(), mappings);
        this.jarRelocatorRunMethod.invoke(relocator);
    }
}

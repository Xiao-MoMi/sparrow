package net.momirealms.sparrow.plugin.dependency;

import net.momirealms.sparrow.plugin.dependency.classloader.IsolatedClassLoader;
import net.momirealms.sparrow.plugin.dependency.exception.DependencyDownloadException;
import net.momirealms.sparrow.plugin.dependency.relocation.Relocation;
import net.momirealms.sparrow.plugin.dependency.relocation.RelocationHandler;
import net.momirealms.sparrow.plugin.Plugin;
import net.momirealms.sparrow.plugin.classpath.ClassPathAppender;
import net.momirealms.sparrow.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public class DependencyManager  {
    private final Path cacheDirectory;
    private final ClassPathAppender sharedClassPathAppender;
    private final ClassPathAppender privateClassPathAppender;
    private final Map<Dependency, Path> loaded = Collections.synchronizedMap(new HashMap<>());
    private final Map<Set<Dependency>, IsolatedClassLoader> loaders = new HashMap<>();
    private final RelocationHandler relocationHandler;
    private final Executor loadingExecutor;
    private final Plugin plugin;

    public DependencyManager(Plugin plugin) {
        this.plugin = plugin;
        this.cacheDirectory = setupCacheDirectory(plugin);
        this.sharedClassPathAppender = plugin.sharedClassPathAppender();
        this.privateClassPathAppender = plugin.privateClassPathAppender();
        this.loadingExecutor = plugin.scheduler().async();
        this.relocationHandler = new RelocationHandler(this);
    }

    /**
     * 获取包含指定依赖项集合的隔离类加载器.
     * 调用此方法前必须确保传入的所有依赖项均已通过 loadDependencies 方法加载完成.
     *
     * @param dependencies 需要包含在类加载器中的依赖项集合.
     * @return 隔离的类加载器 (IsolatedClassLoader) 实例.
     * @throws IllegalStateException 如果请求的依赖项中有任何一个尚未被加载.
     * @throws RuntimeException 如果在转换文件路径为 URL 时发生 MalformedURLException.
     */
    public ClassLoader obtainClassLoaderWith(Set<Dependency> dependencies) {
        // 检查传入的每一个依赖项是否已经成功加载, 若有未加载的则抛出异常.
        Set<Dependency> set = new HashSet<>(dependencies);
        for (Dependency dependency : dependencies) {
            if (!this.loaded.containsKey(dependency)) {
                throw new IllegalStateException("Dependency " + dependency.artifactId() + " is not loaded.");
            }
        }

        // 查询是否已经存在包含相同依赖项集合的缓存类加载器.
        synchronized (this.loaders) {
            // 如果缓存存在, 直接返回缓存的类加载器.
            IsolatedClassLoader classLoader = this.loaders.get(set);
            if (classLoader != null) {
                return classLoader;
            }

            // 如果缓存不存在, 收集所有依赖项对应的本地 jar 文件 URL, 创建新的 IsolatedClassLoader, 存入缓存并返回.
            URL[] urls = set.stream()
                    .map(this.loaded::get)
                    .map(file -> {
                        try {
                            return file.toUri().toURL();
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toArray(URL[]::new);

            classLoader = new IsolatedClassLoader(urls);
            this.loaders.put(set, classLoader);
            return classLoader;
        }
    }

    /**
     * 异步并行加载多个依赖项.
     *
     * @param dependencies 需要加载的依赖项集合.
     */
    public void loadDependencies(Collection<Dependency> dependencies) {
        // 创建与依赖项数量相等的 CountDownLatch 用于同步等待.
        CountDownLatch latch = new CountDownLatch(dependencies.size());

        for (Dependency dependency : dependencies) {
            // 已经加载的依赖项直接跳过并减少计数.
            if (this.loaded.containsKey(dependency)) {
                latch.countDown();
                continue;
            }

            // 对于未加载的依赖项, 将加载任务提交到异步线程池.
            this.loadingExecutor.execute(() -> {
                try {
                    loadDependency(dependency);
                } catch (Throwable e) {
                    this.plugin.logger().warn("Unable to load dependency " + dependency.artifactId(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 主线程调用 await 阻塞, 直到所有异步加载任务执行完毕.
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 加载单个依赖项的内部方法.
     *
     * @param dependency 需要加载的依赖项.
     * @throws Exception 如果下载或重映射过程中发生任何异常.
     */
    private void loadDependency(Dependency dependency) throws Exception {
        // 再次检查缓存, 防止并发加载同一个依赖项.
        if (this.loaded.containsKey(dependency)) return;

        // 调用 downloadDependency 获取或下载依赖项的原始 jar 文件.
        Path downloadDependency = downloadDependency(dependency);

        // 调用 remapDependency 对原始 jar 文件进行类名重定位, 然后存入 loaded 缓存中.
        Path file = remapDependency(dependency, downloadDependency);
        this.loaded.put(dependency, file);

        // 如果该依赖项被标记为自动加载 (autoLoad), 则根据 shared 属性将其追加到共享或私有类路径中.
        if (!dependency.autoLoad()) return;
        if (dependency.visibility() == Dependency.Visibility.PUBLIC) {
            if (this.sharedClassPathAppender != null) {
                this.sharedClassPathAppender.addJarToClasspath(file);
            }
        } else {
            if (this.privateClassPathAppender != null) {
                this.privateClassPathAppender.addJarToClasspath(file);
            }
        }
    }

    /**
     * 下载指定的依赖项到本地缓存目录.
     *
     * @param dependency 需要下载的依赖项.
     * @return 下载并保存到本地的 jar 文件路径.
     * @throws DependencyDownloadException 如果从所有配置的仓库中下载均失败.
     * @throws RuntimeException 如果在清理旧版本或解压内嵌 jar 时发生 I/O 错误.
     */
    private Path downloadDependency(Dependency dependency) throws DependencyDownloadException {
        // 根据依赖项坐标确定其在本地的存储路径, 如果文件存在, 则无需重复下载.
        String fileName = dependency.fileName(null);
        Path file = this.cacheDirectory.resolve(dependency.toLocalPath()).resolve(fileName);
        if (Files.exists(file)) {
            return file;
        }

        // 在下载之前删除已存在的旧版本的jar.
        Path versionFolder = file.getParent().getParent();
        if (Files.exists(versionFolder) && Files.isDirectory(versionFolder)) {
            String version = dependency.version();
            try (Stream<Path> dirStream = Files.list(versionFolder)) {
                dirStream.filter(Files::isDirectory)
                        .filter(it -> !it.getFileName().toString().equals(version))
                        .forEach(dir -> {
                            try {
                                FileUtils.deleteDirectory(dir);
                                if (dependency.hasJarInJarPath()) return; // 禁止 jarinjar 依赖打印垃圾日志
                                plugin.logger().info("Cleaned up outdated dependency " + dir);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("Failed to clean " + versionFolder, e);
            }
        }
        // 如果依赖项配置为 jar-in-jar, 则从类加载器资源中提取该文件并保存.
        if (dependency.source() instanceof Dependency.Source.JarInJar jarInJarSource) {
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(jarInJarSource.jarInJarPath())) {
                if (in != null) {
                    Files.createDirectories(file.getParent());
                    Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return file;
                }
                throw new RuntimeException("Failed to find " + jarInJarSource.jarInJarPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save " + jarInJarSource.jarInJarPath(), e);
            }
        }
        // 正常通过依赖仓库下载依赖到本地.
        else {
            DependencyDownloadException lastError = null;
            List<DependencyRepository> repository = DependencyRepository.getByID("maven");
            if (!repository.isEmpty()) {
                int i = 0;
                while (i < repository.size()) {
                    try {
                        this.plugin.logger().info("Downloading dependency " + repository.get(i).getUrl() + dependency.mavenPath());
                        repository.get(i).download(dependency, file);
                        this.plugin.logger().info("Successfully downloaded " + fileName);
                        return file;
                    } catch (DependencyDownloadException e) {
                        lastError = e;
                        i++;
                    }
                }
            }
            throw Objects.requireNonNull(lastError);
        }
    }

    /**
     * 对已下载的依赖项进行类名 Relocate 处理.
     *
     * @param dependency 依赖项对象, 包含重定位规则.
     * @param normalFile 原始下载的 jar 文件路径.
     * @return 重映射后的 jar 文件路径. 如果没有重定位规则, 则直接返回原始文件路径.
     * @throws Exception 如果在使用 jar-relocator 进行重映射时发生异常.
     */
    private Path remapDependency(Dependency dependency, Path normalFile) throws Exception {
        // 如果没有配置重定位规则则直接返回原始文件路径.
        List<Relocation> rules = new ArrayList<>(dependency.relocations());
        if (rules.isEmpty()) {
            return normalFile;
        }

        // 缓存文件名包含规则指纹, 包名或目标路径变化后重新重定位.
        Path remappedFile = this.cacheDirectory.resolve(dependency.toLocalPath()).resolve(dependency.fileName("remapped-" + Integer.toHexString(rules.hashCode())));
        if (Files.exists(remappedFile)) {
            return remappedFile;
        }

        // 重映射依赖文件.
        plugin.logger().info("Remapping " + dependency.fileName(null));
        relocationHandler.remap(normalFile, remappedFile, rules);
        plugin.logger().info("Successfully remapped " + dependency.fileName(null));
        return remappedFile;
    }

    /**
     * 初始化用于存放依赖项 jar 文件的缓存目录.
     *
     * @param plugin 插件实例, 用于获取数据文件夹路径.
     * @return 缓存目录的 Path 对象.
     * @throws RuntimeException 如果无法创建目录且捕获到其他类型的 IOException.
     */
    private static Path setupCacheDirectory(Plugin plugin) {
        Path cacheDirectory = plugin.dataFolderPath().resolve("libs");
        try {
            if (Files.exists(cacheDirectory) && (Files.isDirectory(cacheDirectory) || Files.isSymbolicLink(cacheDirectory))) {
                return cacheDirectory;
            }

            try {
                Files.createDirectories(cacheDirectory);
            } catch (FileAlreadyExistsException ignore) {
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to create libs directory", e);
        }

        return cacheDirectory;
    }

    /**
     * 关闭依赖管理器, 释放所有缓存的隔离类加载器资源.
     */
    public void close() {
        IOException firstEx = null;

        for (IsolatedClassLoader loader : this.loaders.values()) {
            try {
                loader.close();
            } catch (IOException ex) {
                if (firstEx == null) {
                    firstEx = ex;
                } else {
                    firstEx.addSuppressed(ex);
                }
            }
        }

        if (firstEx != null) {
            plugin.logger().error(firstEx.getMessage(), firstEx);
        }
    }
}

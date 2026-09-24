package net.momirealms.sparrow.plugin;

import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.command.BukkitCommandManager;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.database.DatabaseManager;
import net.momirealms.sparrow.plugin.configuration.ConfigurationManager;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.dependency.Dependency;
import net.momirealms.sparrow.plugin.dependency.Dependencies;
import net.momirealms.sparrow.plugin.dependency.DependencyManager;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.locale.TranslationManagerImpl;
import net.momirealms.sparrow.plugin.classpath.ClassPathAppender;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.plugin.logger.filter.DisconnectLogFilter;
import net.momirealms.sparrow.proxy.BukkitProxy;
import net.momirealms.sparrow.redis.RedisConnector;
import net.momirealms.sparrow.ui.SparrowUI;
import net.momirealms.sparrow.plugin.scheduler.BukkitSchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.util.CharacterUtils;
import net.momirealms.sparrow.util.ExceptionCollector;
import net.momirealms.sparrow.util.ReflectionUtils;
import net.momirealms.sparrow.util.ServerUtils;
import net.momirealms.sparrow.util.VersionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SparrowPlugin implements Plugin {
    private static SparrowPlugin instance;

    private final PluginLogger logger;
    private final Path dataFolderPath;
    private final ClassPathAppender sharedClassPathAppender;
    private final ClassPathAppender privateClassPathAppender;

    private final SchedulerAdapter scheduler;
    private final DependencyManager dependencyManager;
    private final ConfigurationManager configurationManager;
    private final DatabaseManager databaseManager;
    private final RedisConnector redisConnector;
    private final CompatibilityManager compatibilityManager;
    private final PlayerManager playerManager;

    private CommandManager commandManager;
    private TranslationManager translationManager;

    private JavaPlugin javaPlugin;
    private final AtomicBoolean reloading = new AtomicBoolean();
    private boolean isInitializing;
    private boolean successfullyLoaded = false;
    private boolean successfullyEnabled = false;


    SparrowPlugin(PluginLogger logger, Path dataFolderPath, ClassPathAppender sharedClassPathAppender, ClassPathAppender privateClassPathAppender) {
        instance = this;
        this.logger = logger;
        this.dataFolderPath = dataFolderPath;
        this.sharedClassPathAppender = sharedClassPathAppender;
        this.privateClassPathAppender = privateClassPathAppender;

        this.scheduler = new BukkitSchedulerAdapter(this);
        this.dependencyManager = new DependencyManager(this);
        this.configurationManager = new ConfigurationManager(this);
        this.configurationManager.reload();
        this.applyDependencies();
        this.setupProxy();
        this.databaseManager = DatabaseManager.create(PluginConfig.database());
        this.redisConnector = new RedisConnector(PluginConfig.redis());
        this.translationManager = new TranslationManagerImpl(this);
        this.translationManager.reload();
        this.compatibilityManager = new CompatibilityManager(this);
        this.playerManager = new PlayerManager();

        ((Logger) LogManager.getRootLogger()).addFilter(new DisconnectLogFilter());
    }

    public static SparrowPlugin instance() {
        return instance;
    }

    void setJavaPlugin(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
    }

    @Override
    public void onPluginLoad() {
        this.databaseManager.initialize();
        try {
            this.redisConnector.initialize();
            this.compatibilityManager.onLoad(); // 集成插件管理器
            this.successfullyLoaded = true;
        } catch (RuntimeException exception) {
            this.redisConnector.close();
            this.databaseManager.close();
            throw exception;
        }
    }

    @Override
    public void onPluginEnable() {
        if (this.successfullyEnabled) {
            logger().error(" ");
            logger().error(" ");
            logger().error(" ");
            logger().error(TranslationManager.console(LogConstants.PLUGIN_RESTART_AT_RUNTIME));
            logger().error(" ");
            logger().error(" ");
            logger().error(" ");
            Bukkit.getPluginManager().disablePlugin(this.javaPlugin);
            return;
        }
        this.successfullyEnabled = true;
        if (!this.successfullyLoaded) {
            logger().error(" ");
            logger().error(" ");
            logger().error(" ");
            logger().error(TranslationManager.console(LogConstants.PLUGIN_ENABLE_FAILED));
            logger().error(TranslationManager.console(LogConstants.PLUGIN_SHUTDOWN_AFTER_FAILURE));
            logger().error(" ");
            logger().error(" ");
            logger().error(" ");
            Bukkit.getServer().shutdown();
            return;
        }
        this.playerManager.onEnable(this.javaPlugin);
        SparrowUI.getInstance().setUp(this.javaPlugin);
        SparrowUI.getInstance().setExceptionHandler(this.logger::warn);
        // 命令管理器
        this.commandManager = new BukkitCommandManager(this);
        this.commandManager.registerDefaultFeatures();
        // 延迟初始化事件
        this.isInitializing = true;
        this.initASMProxies(); // Proxy 类测试, 仅 dev 模式下生效
        // 集成插件管理器
        this.compatibilityManager.onEnable();
        this.scheduler.platform().runDelayed(this::onServerLoaded);
    }

    public void onServerLoaded() {
        // 集成插件管理器
        this.compatibilityManager.onDelayedEnable();
        // 标记
        this.isInitializing = false;
    }

    @Override
    public void onPluginDisable() {
        if (this.playerManager != null) this.playerManager.shutdown();
        if (this.scheduler != null) this.scheduler.shutdownScheduler();
        if (this.scheduler != null) this.scheduler.shutdownExecutor();
        if (this.redisConnector != null) this.redisConnector.close();
        if (this.databaseManager != null) this.databaseManager.close();
        if (this.dependencyManager != null) this.dependencyManager.close();
        if (ServerUtils.isRunning()) {
            logger().error(" ");
            logger().error(" ");
            logger().error(" ");
            logger().error(TranslationManager.console(LogConstants.PLUGIN_DISABLE_AT_RUNTIME));
            logger().error(" ");
            logger().error(" ");
            logger().error(" ");
            Bukkit.getServer().shutdown();
        }
    }

    /**
     * 创建依赖管理器, 下载并加载插件依赖.
     * 该方法会收集通用依赖与平台依赖, 然后统一交由依赖管理器进行下载和类路径注入.
     * 依赖由 `commonDependencies()` 和 `platformDependencies()` 的返回结果共同决定.
     */
    public void applyDependencies() {
        ArrayList<Dependency> dependenciesToLoad = new ArrayList<>(this.platformDependencies());
        this.dependencyManager.loadDependencies(dependenciesToLoad);
    }

    @Override
    public void setupProxy() {
        BukkitProxy.init(VersionHelper.MINECRAFT_VERSION.version(), this.getPatches(), DependencyVersions.ASM_CLASS_PREFIX);
    }

    /**
     * 以异步和同步两阶段执行插件重载.
     * 提交前占用重载状态, 异步阶段成功后才执行同步阶段; 阶段失败或任务提交被拒绝时释放状态并返回失败结果.
     *
     * @param asyncExecutor 异步任务执行器
     * @param syncExecutor 同步任务执行器
     * @return 一个异步完成的重载结果对象, 包含成功状态, 异步耗时, 同步耗时和问题数量
     */
    public CompletableFuture<ReloadResult> reloadPlugin(Executor asyncExecutor, Executor syncExecutor) {
        if (!this.reloading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(ReloadResult.failure());
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                this.configurationManager.reload();
                this.translationManager.reload();
                // TODO 执行异步重载任务
                return System.currentTimeMillis() - startTime;
            }, asyncExecutor).thenApplyAsync(asyncTime -> {
                long syncStartTime = System.currentTimeMillis();
                // TODO 执行同步重载任务
                long syncTime = System.currentTimeMillis() - syncStartTime;
                return ReloadResult.success(asyncTime, syncTime, 0);
            }, syncExecutor).handle((result, error) -> {
                this.reloading.set(false);
                if (error == null) return result;
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                this.logger().warn(TranslationManager.console(LogConstants.PLUGIN_RELOAD_FAILED), cause);
                return ReloadResult.failure();
            });
        } catch (RuntimeException e) {
            this.reloading.set(false);
            this.logger().warn(TranslationManager.console(LogConstants.PLUGIN_RELOAD_SUBMISSION_FAILED), e);
            return CompletableFuture.completedFuture(ReloadResult.failure());
        }
    }

    /**
     * 重载结果数据类.
     *
     * @param success 是否重载成功
     * @param asyncTime 异步阶段耗时, 单位为毫秒
     * @param syncTime 同步阶段耗时, 单位为毫秒
     * @param issues 重载过程中记录的问题数量
     */
    public record ReloadResult(boolean success, long asyncTime, long syncTime, int issues) {

        static ReloadResult failure() {
            return new ReloadResult(false, -1L, -1L, -1);
        }

        static ReloadResult success(long asyncTime, long syncTime, int issues) {
            return new ReloadResult(true, asyncTime, syncTime, issues);
        }
    }

    @Override
    public List<Dependency> platformDependencies() {
        List<Dependency> dependencies = new ArrayList<>(List.of(
                Dependencies.PLUGIN_BUKKIT_PROXY,
                // Common
                Dependencies.CAFFEINE,
                // Lettuce
                Dependencies.LETTUCE,
                Dependencies.REACTOR_CORE, Dependencies.REACTIVE_STREAMS,
                Dependencies.NETTY_RESOLVER, Dependencies.NETTY_RESOLVER_DNS, Dependencies.NETTY_CODEC_DNS,
                Dependencies.JACKSON_CORE, Dependencies.JACKSON_ANNOTATIONS, Dependencies.JACKSON_DATABIND, Dependencies.JACKSON_DATATYPE,
                // CLOUD
                Dependencies.GEANTY_REF,
                Dependencies.CLOUD_CORE, Dependencies.CLOUD_SERVICES,
                Dependencies.CLOUD_BUKKIT, Dependencies.CLOUD_PAPER, Dependencies.CLOUD_BRIGADIER, Dependencies.CLOUD_MINECRAFT_EXTRAS,
                // Adventure
                Dependencies.OPTION,
                Dependencies.EXAMINATION_API, Dependencies.EXAMINATION_STRING,
                Dependencies.ADVENTURE_KEY, Dependencies.ADVENTURE_API, Dependencies.ADVENTURE_NBT,
                Dependencies.MINIMESSAGE,
                Dependencies.TEXT_SERIALIZER_COMMONS, Dependencies.TEXT_SERIALIZER_LEGACY, Dependencies.TEXT_SERIALIZER_PLAIN, Dependencies.TEXT_SERIALIZER_GSON, Dependencies.TEXT_SERIALIZER_GSON_LEGACY, Dependencies.TEXT_SERIALIZER_JSON
        ));
        switch (PluginConfig.database().type()) {
            case MONGODB -> dependencies.addAll(List.of(
                    Dependencies.MONGODB_DRIVER_BSON, Dependencies.MONGODB_DRIVER_CORE, Dependencies.MONGODB_DRIVER_SYNC
            ));
            case MYSQL -> dependencies.addAll(List.of(
                    Dependencies.JDBI_CORE, Dependencies.HIKARI_CP, Dependencies.MYSQL_DRIVER
            ));
            case MARIADB -> dependencies.addAll(List.of(
                    Dependencies.JDBI_CORE, Dependencies.HIKARI_CP, Dependencies.MARIADB_DRIVER
            ));
            case POSTGRESQL -> dependencies.addAll(List.of(
                    Dependencies.JDBI_CORE, Dependencies.HIKARI_CP, Dependencies.POSTGRESQL_DRIVER, Dependencies.CHECKER_QUAL
            ));
        }
        return dependencies;
    }

    /**
     * 在 dev 环境中预加载内嵌代理 Jar 中的所有类.
     *
     * @throws RuntimeException 当收集到任意类加载异常时抛出
     */
    private void initASMProxies() {
        if (!VersionHelper.IS_RUNNING_IN_DEV) return;
        this.logger().info(TranslationManager.console(LogConstants.PLUGIN_INITIALIZING_PROXIES));
        ClassLoader classLoader = ReflectionUtils.class.getClassLoader();
        ExceptionCollector<Throwable> collector = new ExceptionCollector<>(Throwable.class);
        try (InputStream resourceAsStream = classLoader.getResourceAsStream(DependencyVersions.PROXY_JAR_NAME)) {
            if (resourceAsStream == null) return;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(resourceAsStream.readAllBytes());
                 ZipInputStream zis = new ZipInputStream(bais)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (!entryName.endsWith(".class")) continue;
                    String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                    try {
                        Class.forName(className);
                    } catch (Throwable e) {
                        collector.add(e);
                    }
                }
            } catch (Throwable e) {
                collector.add(e);
            }
        } catch (Throwable e) {
            collector.add(e);
        }
        try {
            collector.throwIfPresent();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 收集当前服务端所具备的补丁标识.
     * 该列表会用于初始化代理层, 以便根据具体发行版差异加载不同兼容逻辑.
     *
     * @return 当前服务端命中的补丁名称列表, 如 `paper`, `folia` 等
     */
    private List<String> getPatches() {
        List<String> patches = new ArrayList<>();
        if (VersionHelper.isPaper()) {
            patches.add("paper");
        }
        if (VersionHelper.isFolia()) {
            patches.add("folia");
        }
        if (VersionHelper.isLeaves()) {
            patches.add("leaves");
        }
        if (VersionHelper.isCanvas()) {
            patches.add("canvas");
        }
        if (VersionHelper.isPurpur()) {
            patches.add("purpur");
        }
        return patches;
    }

    @Override
    public InputStream resourceStream(String filePath) {
        return getResource(CharacterUtils.replaceBackslashWithSlash(filePath));
    }

    private @Nullable InputStream getResource(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("filename cannot be null");
        }
        try {
            URL url = this.getClass().getClassLoader().getResource(filename);
            if (url == null) {
                return null;
            }
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            return connection.getInputStream();
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public File dataFolderFile() {
        return this.dataFolderPath.toFile();
    }

    @Override
    public Path dataFolderPath() {
        return this.dataFolderPath;
    }

    @SuppressWarnings("deprecation")
    @Override
    public String pluginVersion() {
        return javaPlugin().getDescription().getVersion();
    }

    public JavaPlugin javaPlugin() {
        return this.javaPlugin;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void saveResource(String resourcePath) {
        if (resourcePath.isEmpty()) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        File outFile = new File(dataFolderFile(), resourcePath);
        if (outFile.exists())
            return;

        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = resourceStream(resourcePath);
        if (in == null)
            return;

        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(dataFolderFile(), resourcePath.substring(0, Math.max(lastIndex, 0)));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            OutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean isReloading() {
        return this.reloading.get();
    }

    @Override
    public boolean isInitializing() {
        return this.isInitializing;
    }

    @Override
    public PluginLogger logger() {
        return this.logger;
    }

    @Override
    public ClassPathAppender sharedClassPathAppender() {
        return this.sharedClassPathAppender;
    }

    @Override
    public ClassPathAppender privateClassPathAppender() {
        return this.privateClassPathAppender;
    }

    @Override
    public SchedulerAdapter scheduler() {
        return this.scheduler;
    }

    @Override
    public DependencyManager dependencyManager() {
        return this.dependencyManager;
    }

    @Override
    public DatabaseManager databaseManager() {
        return this.databaseManager;
    }

    @Override
    public RedisConnector redisConnector() {
        return this.redisConnector;
    }

    @Override
    public CompatibilityManager compatibilityManager() {
        return this.compatibilityManager;
    }

    @Override
    public ConfigurationManager configurationManager() {
        return this.configurationManager;
    }

    @Override
    public TranslationManager translationManager() {
        return this.translationManager;
    }

    @NotNull
    public PlayerManager playerManager() {
        return this.playerManager;
    }
}

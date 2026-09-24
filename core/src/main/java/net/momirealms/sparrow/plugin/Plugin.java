package net.momirealms.sparrow.plugin;

import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.feature.FeatureManager;
import net.momirealms.sparrow.database.DatabaseManager;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.configuration.ConfigurationManager;
import net.momirealms.sparrow.plugin.dependency.Dependency;
import net.momirealms.sparrow.plugin.dependency.DependencyManager;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.classpath.ClassPathAppender;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.redis.RedisConnector;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface Plugin {

    boolean isReloading();

    boolean isInitializing();

    String pluginVersion();

    PluginLogger logger();

    File dataFolderFile();

    Path dataFolderPath();

    void onPluginLoad();

    void onPluginEnable();

    void onPluginDisable();

    List<Dependency> platformDependencies();

    void setupProxy();

    /**
     * 根据路径读取插件Jar内的资源流.
     *
     * @param filePath 资源相对路径, 允许包含反斜杠路径分隔符
     * @return 资源输入流, 若资源不存在则返回 null
     */
    InputStream resourceStream(String filePath);

    /**
     * 将插件内置资源保存到数据目录.
     * 该方法会在目标文件不存在时创建父目录, 然后从插件资源中读取数据并写入磁盘.
     *
     * @param filePath 需要保存的资源相对路径
     * @throws IllegalArgumentException 当资源路径为空字符串时抛出
     * @throws RuntimeException 当资源复制过程中发生 I/O 异常时抛出
     */
    void saveResource(String filePath);

    ClassPathAppender sharedClassPathAppender();

    ClassPathAppender privateClassPathAppender();

    SchedulerAdapter scheduler();

    DependencyManager dependencyManager();

    DatabaseManager databaseManager();

    RedisConnector redisConnector();

    CompatibilityManager compatibilityManager();

    ConfigurationManager configurationManager();

    TranslationManager translationManager();

    PlayerManager playerManager();

    FeatureManager featureManager();
}
